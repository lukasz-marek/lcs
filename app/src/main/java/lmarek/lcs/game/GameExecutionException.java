package lmarek.lcs.game;

/** Indicates an arena or agent failure, rather than a rules-level game result. */
public final class GameExecutionException extends RuntimeException {
  public GameExecutionException(String message) {
    super(message);
  }
}
