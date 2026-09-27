package lmarek.lcs.arena;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import lmarek.lcs.agent.MctsDecisionTrace;
import lmarek.lcs.agent.XcsDecisionTrace;
import org.junit.jupiter.api.Test;

class DecisionTraceViewTest {
  @Test
  void retainsAnExploratoryChoiceOutsideTheHighestSixteenPredictions() {
    var predictions = new LinkedHashMap<String, Double>();
    for (int index = 0; index < 30; index++) {
      predictions.put("action-" + index, (double) index);
    }
    var trace =
        new XcsDecisionTrace(predictions, "action-0", true, List.of(), List.of(), List.of());

    var view = DecisionTraceView.from(trace, "action-0");

    assertThat(view.actions()).hasSize(16);
    assertThat(view.actions().getFirst().actionId()).isEqualTo(view.selectedAction());
    assertThat(view.actions()).extracting(ActionScoreView::actionId).doesNotHaveDuplicates();
    assertThat(view.legalMoveCount()).isEqualTo(30);
  }

  @Test
  void retainsTheSelectedSearchMoveWhenMoreThanSixteenMovesTie() {
    var visits = new LinkedHashMap<String, Long>();
    var values = new LinkedHashMap<String, Double>();
    for (int index = 0; index < 30; index++) {
      visits.put("action-" + index, 1L);
      values.put("action-" + index, 0.0);
    }
    var trace = new MctsDecisionTrace("action-9", visits, values, 30, 0);

    var view = DecisionTraceView.from(trace, "action-9");

    assertThat(view.actions()).hasSize(16);
    assertThat(view.actions().getFirst().actionId()).isEqualTo("action-9");
    assertThat(view.legalMoveCount()).isEqualTo(30);
  }
}
