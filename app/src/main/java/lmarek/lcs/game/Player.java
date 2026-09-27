package lmarek.lcs.game;

/** The two seats in a deterministic, alternating-turn game. */
public enum Player {
  WHITE,
  BLACK;

  public Player opponent() {
    return this == WHITE ? BLACK : WHITE;
  }
}
