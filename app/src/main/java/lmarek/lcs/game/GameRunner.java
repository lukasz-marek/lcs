package lmarek.lcs.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lmarek.lcs.agent.Agent;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.EpisodeContext;
import lmarek.lcs.agent.EpisodeResult;
import lmarek.lcs.agent.MoveTransition;
import lmarek.lcs.agent.PlayedTurn;

/**
 * Runs one episode, validates agent choices, and delivers the complete lifecycle to both agents.
 */
public final class GameRunner<S, M> {
  private final TurnBasedGame<S, M> game;
  private final int maximumPlies;

  public GameRunner(TurnBasedGame<S, M> game, int maximumPlies) {
    this.game = Objects.requireNonNull(game);
    if (maximumPlies < 1) {
      throw new IllegalArgumentException("maximumPlies must be positive");
    }
    this.maximumPlies = maximumPlies;
  }

  public EpisodeResult<S, M> run(
      Agent<S, M> white,
      Agent<S, M> black,
      long episodeNumber,
      boolean training,
      GameObserver<S, M> observer) {
    var initialState = game.initialState();
    try {
      invokeAll(
          () ->
              white.beginEpisode(
                  new EpisodeContext<>(initialState, Player.WHITE, training, episodeNumber)),
          () ->
              black.beginEpisode(
                  new EpisodeContext<>(initialState, Player.BLACK, training, episodeNumber)));

      var turns = new ArrayList<PlayedTurn<S, M>>();
      var state = initialState;
      var analysis = game.analyze(state);
      var initialOutcome = analysis.outcome();
      if (initialOutcome.isPresent()) {
        return finish(
            white,
            black,
            initialState,
            state,
            initialOutcome.orElseThrow(),
            turns,
            training,
            episodeNumber);
      }
      for (int ply = 0; ply < maximumPlies; ply++) {
        var player = game.playerToMove(state);
        var legalMoves = analysis.legalMoves();
        if (legalMoves.isEmpty()) {
          throw new GameExecutionException("Game reported neither an outcome nor a legal move");
        }
        var agent = player == Player.WHITE ? white : black;
        var decision =
            Objects.requireNonNull(
                agent.decide(
                    new DecisionContext<>(state, player, legalMoves, ply, training, episodeNumber)),
                "Agent returned no decision");
        if (!legalMoves.contains(decision.move())) {
          throw new GameExecutionException(
              "Agent %s selected an illegal move: %s".formatted(agent.id(), decision.move()));
        }

        var next = game.applyAnalyzedMove(analysis, decision.move());
        var transition = new MoveTransition<>(state, decision.move(), next, player, ply);
        var turn = new PlayedTurn<>(transition, decision);
        turns.add(turn);
        invokeAll(
            () -> white.observe(transition),
            () -> black.observe(transition),
            () -> observer.turnCompleted(turn));
        state = next;
        analysis = game.analyze(state);
        var outcomeAfterTurn = analysis.outcome();
        if (outcomeAfterTurn.isPresent()) {
          return finish(
              white,
              black,
              initialState,
              state,
              outcomeAfterTurn.orElseThrow(),
              turns,
              training,
              episodeNumber);
        }
      }
      throw new GameExecutionException(
          "Game exceeded the %d-ply safety limit".formatted(maximumPlies));
    } catch (RuntimeException | Error failure) {
      abortBoth(white, black, failure);
      throw failure;
    }
  }

  private EpisodeResult<S, M> finish(
      Agent<S, M> white,
      Agent<S, M> black,
      S initialState,
      S finalState,
      GameOutcome outcome,
      List<PlayedTurn<S, M>> turns,
      boolean training,
      long episodeNumber) {
    var whiteResult =
        new EpisodeResult<>(
            initialState, finalState, outcome, turns, Player.WHITE, training, episodeNumber);
    var blackResult = whiteResult.forSeat(Player.BLACK);
    invokeAll(() -> white.endEpisode(whiteResult), () -> black.endEpisode(blackResult));
    return whiteResult;
  }

  private static void invokeAll(Runnable... callbacks) {
    Throwable firstFailure = null;
    for (var callback : callbacks) {
      try {
        callback.run();
      } catch (RuntimeException | Error failure) {
        if (firstFailure == null) {
          firstFailure = failure;
        } else if (firstFailure != failure) {
          firstFailure.addSuppressed(failure);
        }
      }
    }
    if (firstFailure instanceof Error error) {
      throw error;
    }
    if (firstFailure instanceof RuntimeException exception) {
      throw exception;
    }
  }

  private static void abortBoth(Agent<?, ?> white, Agent<?, ?> black, Throwable primary) {
    for (var agent : List.of(white, black)) {
      try {
        agent.abortEpisode();
      } catch (RuntimeException | Error abortFailure) {
        if (abortFailure != primary) {
          primary.addSuppressed(abortFailure);
        }
      }
    }
  }
}
