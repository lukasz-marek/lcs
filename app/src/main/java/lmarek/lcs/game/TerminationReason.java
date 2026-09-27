package lmarek.lcs.game;

/** A rules-level reason why a completed game ended. */
public enum TerminationReason {
  NO_PIECES,
  NO_LEGAL_MOVES,
  THREEFOLD_REPETITION,
  KING_ONLY_MOVE_LIMIT,
  LIMITED_ENDGAME_MOVE_LIMIT
}
