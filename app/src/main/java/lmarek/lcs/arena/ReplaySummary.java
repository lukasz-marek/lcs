package lmarek.lcs.arena;

public record ReplaySummary(
    long gameNumber,
    boolean evaluation,
    String whiteCompetitorId,
    String blackCompetitorId,
    String result,
    int length) {
  static ReplaySummary from(GameReplay replay) {
    return new ReplaySummary(
        replay.gameNumber(),
        replay.evaluation(),
        replay.whiteCompetitorId(),
        replay.blackCompetitorId(),
        replay.result(),
        replay.turns().size());
  }
}
