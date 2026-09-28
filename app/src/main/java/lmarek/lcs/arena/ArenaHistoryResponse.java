package lmarek.lcs.arena;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ArenaHistoryResponse(
    String runId,
    long revision,
    HistoryRevisions historyRevisions,
    @Nullable Map<String, Map<String, List<ChartPoint>>> charts,
    @Nullable List<ReplaySummary> recentReplays,
    @Nullable List<EvolutionHighlight> evolutionHighlights) {
  static ArenaHistoryResponse from(
      ArenaSnapshot snapshot, String runId, long charts, long replays, long evolution) {
    var versions = snapshot.historyRevisions();
    boolean sameRun = snapshot.runId().equals(runId);
    return new ArenaHistoryResponse(
        snapshot.runId(),
        snapshot.revision(),
        versions,
        sameRun && versions.charts() == charts ? null : snapshot.charts(),
        sameRun && versions.replays() == replays ? null : snapshot.recentReplays(),
        sameRun && versions.evolution() == evolution ? null : snapshot.evolutionHighlights());
  }
}
