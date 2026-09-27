package lmarek.lcs.game;

import java.util.List;
import java.util.Optional;

/** Rules needed by agents and runners without coupling them to a particular game. */
public interface TurnBasedGame<S, M> {
  S initialState();

  Player playerToMove(S state);

  List<M> legalMoves(S state);

  S applyMove(S state, M move);

  Optional<GameOutcome> outcome(S state);

  default PositionAnalysis<S, M> analyze(S state) {
    return PositionAnalysis.analyze(this, state);
  }

  default Optional<GameOutcome> outcomeFromLegalMoves(S state, List<M> legalMoves) {
    return outcome(state);
  }

  default S applyAnalyzedMove(PositionAnalysis<S, M> analysis, M move) {
    return applyMove(analysis.validate(this, move), move);
  }
}
