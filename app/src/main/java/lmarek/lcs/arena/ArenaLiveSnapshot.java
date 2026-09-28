package lmarek.lcs.arena;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record ArenaLiveSnapshot(
    String runId,
    long revision,
    RunStatus status,
    Pacing pacing,
    Instant createdAt,
    String seed,
    List<CompetitorView> competitors,
    long trainingGames,
    long evaluationGames,
    ArenaStatisticsView statistics,
    @Nullable CurrentGameView currentGame,
    Map<String, Map<String, Double>> telemetry,
    HistoryRevisions historyRevisions,
    @Nullable String lastError) {
  static ArenaLiveSnapshot from(ArenaSnapshot snapshot) {
    return new ArenaLiveSnapshot(
        snapshot.runId(),
        snapshot.revision(),
        snapshot.status(),
        snapshot.pacing(),
        snapshot.createdAt(),
        snapshot.seed(),
        snapshot.competitors(),
        snapshot.trainingGames(),
        snapshot.evaluationGames(),
        snapshot.statistics(),
        snapshot.currentGame(),
        snapshot.telemetry(),
        snapshot.historyRevisions(),
        snapshot.lastError());
  }
}
