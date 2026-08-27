package lmarek.lcs.classifier.rule;

import java.util.List;
import java.util.Map;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MatchSetTest {
  private static final Matcher MATCH_GREEN = Matcher.oneOf(Symbol.of("green"));
  private static final Matcher MATCH_BLUE = Matcher.oneOf(Symbol.of("blue"));
  private static final Symbol PREDICTION_RED = Symbol.of("red");
  private static final Symbol PREDICTION_YELLOW = Symbol.of("yellow");

  @Test
  void returnsAllClassifiers() {
    // given
    var allRules =
        List.of(
            new ClassifierBuilder().addMatchers(MATCH_GREEN).prediction(PREDICTION_RED).build(),
            new ClassifierBuilder().addMatchers(MATCH_BLUE).prediction(PREDICTION_YELLOW).build());
    // when
    var built = new MatchSetBuilder().addAllClassifiers(allRules).build();

    // then
    Assertions.assertThat(built.classifiers()).containsExactlyInAnyOrderElementsOf(allRules);
  }

  @Test
  void groupsClassifiersByPrediction() {
    // given
    var allRules =
        List.of(
            new ClassifierBuilder().addMatchers(MATCH_GREEN).prediction(PREDICTION_RED).build(),
            new ClassifierBuilder().addMatchers(MATCH_BLUE).prediction(PREDICTION_YELLOW).build());
    var sut = new MatchSetBuilder().addAllClassifiers(allRules).build();
    var expected =
        Map.of(
            PREDICTION_RED, List.of(allRules.get(0)), PREDICTION_YELLOW, List.of(allRules.get(1)));

    // when
    var result = sut.rulesByPrediction();

    // then
    Assertions.assertThat(result).isEqualTo(expected);
  }

  @Test
  void groupsClassifiersByPredictionWithDuplicatePredictions() {
    // given
    var allRules =
        List.of(
            new ClassifierBuilder().addMatchers(MATCH_GREEN).prediction(PREDICTION_YELLOW).build(),
            new ClassifierBuilder().addMatchers(MATCH_BLUE).prediction(PREDICTION_YELLOW).build());
    var sut = new MatchSetBuilder().addAllClassifiers(allRules).build();
    var expected = Map.of(PREDICTION_YELLOW, allRules);

    // when
    var result = sut.rulesByPrediction();

    // then
    Assertions.assertThat(result).isEqualTo(expected);
  }
}
