package lmarek.lcs.arena;

import java.util.List;
import java.util.Objects;

public record GameReplay(
    long gameNumber,
    boolean evaluation,
    String whiteCompetitorId,
    String blackCompetitorId,
    BoardView initialBoard,
    List<ReplayTurn> turns,
    String result,
    String terminationReason) {
  public GameReplay {
    Objects.requireNonNull(whiteCompetitorId);
    Objects.requireNonNull(blackCompetitorId);
    Objects.requireNonNull(initialBoard);
    turns = List.copyOf(turns);
    Objects.requireNonNull(result);
    Objects.requireNonNull(terminationReason);
  }
}
