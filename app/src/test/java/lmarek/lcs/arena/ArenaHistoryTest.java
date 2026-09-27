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

  private static GameReplay replay(long game, String white, String black, String result) {
    var emptyBoard = new BoardView(List.of(), List.of(), List.of(), List.of());
    return new GameReplay(game, false, white, black, emptyBoard, List.of(), result, "NO_PIECES");
  }
}
