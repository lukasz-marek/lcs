package lmarek.lcs.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.MctsDecisionTrace;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.TerminationReason;
import lmarek.lcs.game.TurnBasedGame;
import org.junit.jupiter.api.Test;

class MctsAgentTest {
  private static final TakeAwayGame TAKE_AWAY = new TakeAwayGame();

  @Test
  void findsAnImmediateForcedWin() {
    var state = new TakeState(2, Player.WHITE);
    var agent =
        new MctsAgent<>(
            "mcts",
            "MCTS",
            TAKE_AWAY,
            new MctsConfig(100, 20, Math.sqrt(2.0)),
            17L,
            Object::toString);

    var decision = agent.decide(context(state, TAKE_AWAY.legalMoves(state)));

    assertThat(decision.move()).isEqualTo(2);
    var trace = (MctsDecisionTrace) decision.trace();
    assertThat(trace.selectedMove()).isEqualTo("2");
    assertThat(trace.meanValues().get("2")).isEqualTo(1.0);
    assertThat(trace.visits().get("2")).isGreaterThan(trace.visits().get("1"));
  }

  @Test
  void backsUpValuesForThePlayerWhoChoseEachTreeEdge() {
    var game = new TrapGame();
    var agent = new MctsAgent<>("mcts", "MCTS", game, new MctsConfig(300, 20, Math.sqrt(2.0)), 31L);

    var decision = agent.decide(context(TrapState.ROOT, game.legalMoves(TrapState.ROOT)));

    assertThat(decision.move()).isEqualTo(TrapMove.SAFE);
  }

  @Test
  void backsUpTheMirroredTrapFromBlacksPerspective() {
    var game = new MirroredTrapGame();
    var agent = new MctsAgent<>("mcts", "MCTS", game, new MctsConfig(300, 20, Math.sqrt(2.0)), 31L);

    var decision =
        agent.decide(
            new DecisionContext<>(
                TrapState.ROOT, Player.BLACK, game.legalMoves(TrapState.ROOT), 0, false, 1L));

    assertThat(decision.move()).isEqualTo(TrapMove.SAFE);
  }

  @Test
  void onlyReturnsMovesFromTheDecisionContext() {
    var state = new TakeState(2, Player.WHITE);
    var agent =
        new MctsAgent<>("mcts", "MCTS", TAKE_AWAY, new MctsConfig(20, 20, Math.sqrt(2.0)), 4L);
    var permitted = List.of(1);

    var decision = agent.decide(context(state, permitted));

    assertThat(decision.move()).isIn(permitted);
  }

  @Test
  void isDeterministicForTheSameSeed() {
    var state = new TakeState(7, Player.WHITE);
    var config = new MctsConfig(80, 20, Math.sqrt(2.0));
    var first = new MctsAgent<>("a", "A", TAKE_AWAY, config, 923L);
    var second = new MctsAgent<>("a", "A", TAKE_AWAY, config, 923L);

    var firstDecision = first.decide(context(state, TAKE_AWAY.legalMoves(state)));
    var secondDecision = second.decide(context(state, TAKE_AWAY.legalMoves(state)));

    assertThat(firstDecision).isEqualTo(secondDecision);
  }

  @Test
  void reportsOneRootVisitPerSimulationAndStartsAFreshTreeForEveryDecision() {
    var state = new TakeState(5, Player.WHITE);
    var config = new MctsConfig(17, 20, Math.sqrt(2.0));
    var agent = new MctsAgent<>("mcts", "MCTS", TAKE_AWAY, config, 91L);

    var first =
        (MctsDecisionTrace) agent.decide(context(state, TAKE_AWAY.legalMoves(state))).trace();
    var second =
        (MctsDecisionTrace) agent.decide(context(state, TAKE_AWAY.legalMoves(state))).trace();

    assertThat(first.visits()).hasSize(2);
    assertThat(sum(first.visits().values())).isEqualTo(17);
    assertThat(sum(second.visits().values())).isEqualTo(17);
  }

  @Test
  void treatsCappedRolloutsAsNeutralAndReportsEveryTruncation() {
    var game = new EndlessGame();
    var state = new EndlessState(Player.WHITE);
    var agent = new MctsAgent<>("mcts", "MCTS", game, new MctsConfig(7, 3, Math.sqrt(2.0)), 99L);

    var trace = (MctsDecisionTrace) agent.decide(context(state, game.legalMoves(state))).trace();

    assertThat(trace.truncatedRollouts()).isEqualTo(7);
    assertThat(trace.meanValues()).containsEntry("ONLY", 0.0);
    assertThat(agent.telemetry().metrics())
        .containsEntry("lastTruncatedRollouts", 7.0)
        .containsEntry("totalTruncatedRollouts", 7.0);
  }

  @Test
  void exposesTheDocumentedSimulationPresets() {
    assertThat(MctsConfig.forPreset(MctsPreset.FAST).simulations()).isEqualTo(100);
    assertThat(MctsConfig.balanced().simulations()).isEqualTo(500);
    assertThat(MctsConfig.forPreset(MctsPreset.STRONG).simulations()).isEqualTo(2_000);
    assertThat(MctsConfig.balanced().rolloutPlyLimit()).isEqualTo(500);
    assertThat(MctsConfig.balanced().explorationConstant()).isEqualTo(Math.sqrt(2.0));
  }

  @Test
  void frozenCopiesUseTheirProvidedSeedAndRemainNonLearning() {
    var source =
        new MctsAgent<>("mcts", "MCTS", TAKE_AWAY, new MctsConfig(30, 20, Math.sqrt(2.0)), 1L);
    var state = new TakeState(6, Player.WHITE);
    var context = context(state, TAKE_AWAY.legalMoves(state));

    var first = source.frozenCopy(77L);
    var second = source.frozenCopy(77L);

    assertThat(source.learns()).isFalse();
    assertThat(first.decide(context)).isEqualTo(second.decide(context));
  }

  @Test
  void interruptionCancelsADeepSearchPromptly() throws InterruptedException {
    var enteredSearch = new CountDownLatch(1);
    var game = new SignallingEndlessGame(enteredSearch);
    var agent =
        new MctsAgent<>("mcts", "MCTS", game, new MctsConfig(5_000, 1_000, Math.sqrt(2.0)), 19L);
    var failure = new AtomicReference<Throwable>();
    var worker =
        Thread.ofPlatform()
            .unstarted(
                () -> {
                  try {
                    agent.decide(context(new EndlessState(Player.WHITE), List.of("ONLY")));
                  } catch (Throwable thrown) {
                    failure.set(thrown);
                  }
                });

    worker.start();
    assertThat(enteredSearch.await(1, TimeUnit.SECONDS)).isTrue();
    worker.interrupt();
    worker.join(2_000);

    assertThat(worker.isAlive()).isFalse();
    assertThat(failure.get()).isInstanceOf(CancellationException.class);
  }

  private static long sum(Iterable<Long> values) {
    long result = 0;
    for (long value : values) {
      result += value;
    }
    return result;
  }

  private static <S, M> DecisionContext<S, M> context(S state, List<M> legalMoves) {
    return new DecisionContext<>(state, Player.WHITE, legalMoves, 0, false, 1L);
  }

  private record TakeState(int stones, Player playerToMove) {}

  private static final class TakeAwayGame implements TurnBasedGame<TakeState, Integer> {
    @Override
    public TakeState initialState() {
      return new TakeState(2, Player.WHITE);
    }

    @Override
    public Player playerToMove(TakeState state) {
      return state.playerToMove();
    }

    @Override
    public List<Integer> legalMoves(TakeState state) {
      if (state.stones() == 0) {
        return List.of();
      }
      return state.stones() == 1 ? List.of(1) : List.of(1, 2);
    }

    @Override
    public TakeState applyMove(TakeState state, Integer move) {
      if (!legalMoves(state).contains(move)) {
        throw new IllegalArgumentException("illegal move");
      }
      return new TakeState(state.stones() - move, state.playerToMove().opponent());
    }

    @Override
    public Optional<GameOutcome> outcome(TakeState state) {
      if (state.stones() == 0) {
        return Optional.of(
            GameOutcome.win(state.playerToMove().opponent(), TerminationReason.NO_PIECES));
      }
      return Optional.empty();
    }
  }

  private record EndlessState(Player playerToMove) {}

  private static final class EndlessGame implements TurnBasedGame<EndlessState, String> {
    @Override
    public EndlessState initialState() {
      return new EndlessState(Player.WHITE);
    }

    @Override
    public Player playerToMove(EndlessState state) {
      return state.playerToMove();
    }

    @Override
    public List<String> legalMoves(EndlessState state) {
      return List.of("ONLY");
    }

    @Override
    public EndlessState applyMove(EndlessState state, String move) {
      return new EndlessState(state.playerToMove().opponent());
    }

    @Override
    public Optional<GameOutcome> outcome(EndlessState state) {
      return Optional.empty();
    }
  }

  private static final class SignallingEndlessGame implements TurnBasedGame<EndlessState, String> {
    private final CountDownLatch enteredSearch;

    private SignallingEndlessGame(CountDownLatch enteredSearch) {
      this.enteredSearch = enteredSearch;
    }

    @Override
    public EndlessState initialState() {
      return new EndlessState(Player.WHITE);
    }

    @Override
    public Player playerToMove(EndlessState state) {
      return state.playerToMove();
    }

    @Override
    public List<String> legalMoves(EndlessState state) {
      enteredSearch.countDown();
      return List.of("ONLY");
    }

    @Override
    public EndlessState applyMove(EndlessState state, String move) {
      return new EndlessState(state.playerToMove().opponent());
    }

    @Override
    public Optional<GameOutcome> outcome(EndlessState state) {
      return Optional.empty();
    }
  }

  private enum TrapState {
    ROOT,
    OPPONENT_CHOICE,
    DRAW,
    WHITE_WIN,
    BLACK_WIN
  }

  private enum TrapMove {
    SAFE,
    TRAP,
    PUNISH,
    BLUNDER
  }

  private static final class TrapGame implements TurnBasedGame<TrapState, TrapMove> {
    @Override
    public TrapState initialState() {
      return TrapState.ROOT;
    }

    @Override
    public Player playerToMove(TrapState state) {
      return state == TrapState.OPPONENT_CHOICE ? Player.BLACK : Player.WHITE;
    }

    @Override
    public List<TrapMove> legalMoves(TrapState state) {
      return switch (state) {
        case ROOT -> List.of(TrapMove.SAFE, TrapMove.TRAP);
        case OPPONENT_CHOICE -> List.of(TrapMove.PUNISH, TrapMove.BLUNDER);
        case DRAW, WHITE_WIN, BLACK_WIN -> List.of();
      };
    }

    @Override
    public TrapState applyMove(TrapState state, TrapMove move) {
      return switch (move) {
        case SAFE -> TrapState.DRAW;
        case TRAP -> TrapState.OPPONENT_CHOICE;
        case PUNISH -> TrapState.BLACK_WIN;
        case BLUNDER -> TrapState.WHITE_WIN;
      };
    }

    @Override
    public Optional<GameOutcome> outcome(TrapState state) {
      return switch (state) {
        case DRAW -> Optional.of(GameOutcome.draw(TerminationReason.THREEFOLD_REPETITION));
        case WHITE_WIN -> Optional.of(GameOutcome.win(Player.WHITE, TerminationReason.NO_PIECES));
        case BLACK_WIN -> Optional.of(GameOutcome.win(Player.BLACK, TerminationReason.NO_PIECES));
        case ROOT, OPPONENT_CHOICE -> Optional.empty();
      };
    }
  }

  private static final class MirroredTrapGame implements TurnBasedGame<TrapState, TrapMove> {
    @Override
    public TrapState initialState() {
      return TrapState.ROOT;
    }

    @Override
    public Player playerToMove(TrapState state) {
      return state == TrapState.OPPONENT_CHOICE ? Player.WHITE : Player.BLACK;
    }

    @Override
    public List<TrapMove> legalMoves(TrapState state) {
      return switch (state) {
        case ROOT -> List.of(TrapMove.SAFE, TrapMove.TRAP);
        case OPPONENT_CHOICE -> List.of(TrapMove.PUNISH, TrapMove.BLUNDER);
        case DRAW, WHITE_WIN, BLACK_WIN -> List.of();
      };
    }

    @Override
    public TrapState applyMove(TrapState state, TrapMove move) {
      return switch (move) {
        case SAFE -> TrapState.DRAW;
        case TRAP -> TrapState.OPPONENT_CHOICE;
        case PUNISH -> TrapState.WHITE_WIN;
        case BLUNDER -> TrapState.BLACK_WIN;
      };
    }

    @Override
    public Optional<GameOutcome> outcome(TrapState state) {
      return switch (state) {
        case DRAW -> Optional.of(GameOutcome.draw(TerminationReason.THREEFOLD_REPETITION));
        case WHITE_WIN -> Optional.of(GameOutcome.win(Player.WHITE, TerminationReason.NO_PIECES));
        case BLACK_WIN -> Optional.of(GameOutcome.win(Player.BLACK, TerminationReason.NO_PIECES));
        case ROOT, OPPONENT_CHOICE -> Optional.empty();
      };
    }
  }
}
