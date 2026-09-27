package lmarek.lcs.game;

import java.util.List;
import java.util.Optional;

/** Immutable analysis bound to the game and state that generated its legal moves. */
public final class PositionAnalysis<S, M> {
  private final TurnBasedGame<S, M> game;
  private final S state;
  private final List<M> legalMoves;
  private final Optional<GameOutcome> outcome;

  private PositionAnalysis(TurnBasedGame<S, M> game, S state) {
    this.game = game;
    this.state = state;
    legalMoves = List.copyOf(game.legalMoves(state));
    outcome = game.outcomeFromLegalMoves(state, legalMoves);
  }

  public static <S, M> PositionAnalysis<S, M> analyze(TurnBasedGame<S, M> game, S state) {
    return new PositionAnalysis<>(game, state);
  }

  public S state() {
    return state;
  }

  public List<M> legalMoves() {
    return legalMoves;
  }

  public Optional<GameOutcome> outcome() {
    return outcome;
  }

  public S validate(TurnBasedGame<S, M> owner, M move) {
    if (game != owner || !legalMoves.contains(move)) {
      throw new IllegalArgumentException("Analysis belongs to another game or move is illegal");
    }
    return state;
  }
}
