package lmarek.lcs.arena;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.TerminationReason;
import org.junit.jupiter.api.Test;

class ArenaHistoryTest {
  @Test
  void boundsDetailsAndCompactsOlderChartSamples() {
    var history = new ArenaHistory(Instant.now());
    for (int game = 1; game <= 2_100; game++) {
      var white = game % 2 == 1 ? "A" : "B";
      var black = white.equals("A") ? "B" : "A";
      var outcome = GameOutcome.win(Player.WHITE, TerminationReason.NO_PIECES);
      history.recordTraining(replay(game, white, black, "A win"), outcome, Map.of(), Map.of());
    }
    for (int event = 1; event <= 250; event++) {
      history.addEvolution(new EvolutionHighlight(event, "A", event, "COVERED", "event"));
    }

    assertThat(history.replaySummaries()).hasSize(ArenaHistory.MAX_REPLAYS);
    assertThat(history.replaySummaries().getFirst().gameNumber()).isEqualTo(2_081);
    assertThat(history.evolutionHighlights()).hasSize(ArenaHistory.MAX_EVOLUTION_EVENTS);
    assertThat(history.evolutionHighlights().getFirst().ruleId()).isEqualTo(51);
    assertThat(history.charts().get("results").get("A wins"))
        .hasSizeLessThanOrEqualTo(ArenaHistory.MAX_CHART_POINTS);
    assertThat(history.charts().get("results").get("A wins").getLast().x()).isEqualTo(2_100);
  }

  @Test
  void reusesImmutableSectionsAndInvalidatesOnlyChangedData() {
    var history = new ArenaHistory(Instant.now());
    var initial = history.view();
    var unchanged = history.view();
    assertThat(unchanged.charts()).isSameAs(initial.charts());
    assertThat(unchanged.replaySummaries()).isSameAs(initial.replaySummaries());
    assertThat(unchanged.evolutionHighlights()).isSameAs(initial.evolutionHighlights());
    history.addEvolution(new EvolutionHighlight(1, "A", 1L, "COVERED", "event"));
    var event = history.view();
    assertThat(event.historyRevisions()).isEqualTo(new HistoryRevisions(0, 0, 1));
    assertThat(event.charts()).isSameAs(initial.charts());
    assertThat(initial.evolutionHighlights()).isEmpty();
    assertThat(event.evolutionHighlights()).hasSize(1);

    history.recordTraining(
        replay(1, "A", "B", "A win"),
        GameOutcome.win(Player.WHITE, TerminationReason.NO_PIECES),
        Map.of("xcs.population.micro", 10.0),
        Map.of());
    var training = history.view();
    assertThat(training.historyRevisions()).isEqualTo(new HistoryRevisions(1, 1, 1));
    assertThat(training.charts().get("learning").get("A population")).hasSize(1);
    assertThat(initial.charts().get("results").get("A wins")).isEmpty();
    assertThat(training.evolutionHighlights()).isSameAs(event.evolutionHighlights());

    history.recordEvaluationReplay(replay(2, "B", "A", "Draw"));
    var evaluation = history.view();
    assertThat(evaluation.historyRevisions()).isEqualTo(new HistoryRevisions(1, 2, 1));
    assertThat(evaluation.charts()).isSameAs(training.charts());
    history.recordEvaluationSeries(1, 0.5, 0.5);
    var scores = history.view();
    assertThat(scores.historyRevisions()).isEqualTo(new HistoryRevisions(2, 2, 1));
    assertThat(scores.replaySummaries()).isSameAs(evaluation.replaySummaries());
    assertThat(scores.charts().get("evaluation").get("A score")).hasSize(1);
    assertThat(history.view().statistics()).isNotSameAs(scores.statistics());
  }

  private static GameReplay replay(long game, String white, String black, String result) {
    var emptyBoard = new BoardView(List.of(), List.of(), List.of(), List.of());
    return new GameReplay(game, false, white, black, emptyBoard, List.of(), result, "NO_PIECES");
  }
}
