package lmarek.lcs.arena;

public enum Pacing {
  LIVE(600),
  TURBO(0);

  private final long delayMillis;

  Pacing(long delayMillis) {
    this.delayMillis = delayMillis;
  }

  public long delayMillis() {
    return delayMillis;
  }
}
