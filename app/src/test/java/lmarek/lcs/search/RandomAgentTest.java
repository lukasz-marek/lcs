package lmarek.lcs.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.RandomDecisionTrace;
import lmarek.lcs.game.Player;
import org.junit.jupiter.api.Test;

class RandomAgentTest {
  @Test
  void returnsOnlyLegalMovesAndReportsTheChoiceSize() {
    var agent = new RandomAgent<String, String>("random", "Random", 7L);
    var context = context(List.of("left", "right", "forward"));

    var decision = agent.decide(context);

    assertThat(decision.move()).isIn(context.legalMoves());
    assertThat(decision.trace()).isEqualTo(new RandomDecisionTrace(3));
  }

  @Test
  void repeatsTheSameSequenceForTheSameSeed() {
    var first = new RandomAgent<String, String>("random", "Random", 123L);
    var second = new RandomAgent<String, String>("random", "Random", 123L);
    var context = context(List.of("a", "b", "c", "d"));

    var firstChoices = choices(first, context, 20);
    var secondChoices = choices(second, context, 20);

    assertThat(firstChoices).isEqualTo(secondChoices).allMatch(context.legalMoves()::contains);
  }

  @Test
  void frozenCopyUsesItsProvidedSeedAndNeverLearns() {
    var source = new RandomAgent<String, String>("random", "Random", 1L);
    var first = source.frozenCopy(55L);
    var second = source.frozenCopy(55L);
    var context = context(List.of("a", "b", "c"));

    assertThat(source.learns()).isFalse();
    assertThat(choices(first, context, 12)).isEqualTo(choices(second, context, 12));
  }

  private static List<String> choices(
      lmarek.lcs.agent.Agent<String, String> agent,
      DecisionContext<String, String> context,
      int count) {
    var result = new ArrayList<String>();
    for (int index = 0; index < count; index++) {
      result.add(agent.decide(context).move());
    }
    return result;
  }

  private static DecisionContext<String, String> context(List<String> legalMoves) {
    return new DecisionContext<>("state", Player.WHITE, legalMoves, 0, false, 1L);
  }
}
