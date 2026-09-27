package lmarek.lcs.draughts;

/** The FMJD material-specific move limits. */
public enum LimitedEndgameKind {
  FIVE_MOVES(5),
  SIXTEEN_MOVES(16);

  private final int movesPerPlayer;

  LimitedEndgameKind(int movesPerPlayer) {
    this.movesPerPlayer = movesPerPlayer;
  }

  public int movesPerPlayer() {
    return movesPerPlayer;
  }
}
