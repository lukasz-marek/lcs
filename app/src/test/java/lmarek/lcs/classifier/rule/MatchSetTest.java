package lmarek.lcs.classifier.rule;

import java.util.List;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MatchSetTest {
  private static final Matcher MATCH_GREEN = Matcher.oneOf(Symbol.of("green"));
  private static final Matcher MATCH_BLUE = Matcher.oneOf(Symbol.of("blue"));
  private static final Symbol PREDICTION_RED = Symbol.of("red");
  private static final Symbol PREDICTION_YELLOW = Symbol.of("yellow");

  @Test
  void returnsAllRules() {
    // given
    var allRules =
        List.of(
            new MatchableRuleBuilder().addMatchers(MATCH_GREEN).prediction(PREDICTION_RED).build(),
            new MatchableRuleBuilder()
                .addMatchers(MATCH_BLUE)
                .prediction(PREDICTION_YELLOW)
                .build());
    // when
    var built = new MatchSetBuilder().addAllRules(allRules).build();

    // then
    Assertions.assertThat(built.rules()).containsExactlyInAnyOrderElementsOf(allRules);
  }
}
