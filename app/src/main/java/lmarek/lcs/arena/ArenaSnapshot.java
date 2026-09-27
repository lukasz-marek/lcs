package lmarek.lcs.arena;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public record ArenaSnapshot(
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
    List<ReplaySummary> recentReplays,
    List<EvolutionHighlight> evolutionHighlights,
    Map<String, Map<String, Double>> telemetry,
    Map<String, Map<String, List<ChartPoint>>> charts,
    @Nullable String lastError) {
  public ArenaSnapshot {
    competitors = List.copyOf(competitors);
    recentReplays = List.copyOf(recentReplays);
    evolutionHighlights = List.copyOf(evolutionHighlights);
    telemetry =
        telemetry.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    charts = Map.copyOf(charts);
  }
}
