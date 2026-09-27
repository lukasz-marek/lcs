package lmarek.lcs.arena;

import java.util.List;
import java.util.Map;

record ArenaHistoryView(
    long trainingGames,
    long evaluationGames,
    ArenaStatisticsView statistics,
    List<ReplaySummary> replaySummaries,
    List<EvolutionHighlight> evolutionHighlights,
    Map<String, Map<String, List<ChartPoint>>> charts) {
  ArenaHistoryView {
    replaySummaries = List.copyOf(replaySummaries);
    evolutionHighlights = List.copyOf(evolutionHighlights);
    charts = Map.copyOf(charts);
  }
}
