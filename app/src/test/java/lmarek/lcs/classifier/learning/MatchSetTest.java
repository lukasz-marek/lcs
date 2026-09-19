package lmarek.lcs.classifier.learning;

import java.util.List;
import java.util.Map;
import lmarek.lcs.classifier.rule.Action;
import lmarek.lcs.classifier.rule.Classifier;
import lmarek.lcs.classifier.rule.ClassifierBuilder;
import lmarek.lcs.classifier.rule.Condition;
import lmarek.lcs.classifier.rule.Matcher;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class MatchSetTest {
  private static final Condition MATCH_GREEN = new Condition(Matcher.oneOf("green"));
  private static final Condition MATCH_BLUE = new Condition(Matcher.oneOf("blue"));
  private static final Action PREDICTION_RED = new Action("red");
  private static final Action PREDICTION_YELLOW = new Action("yellow");

  @Test
  void returnsAllClassifiers() {
    // given
    var allRules =
        List.of(
            new ClassifierBuilder()
                .condition(MATCH_GREEN)
                .action(PREDICTION_RED)
                .metadata(Classifier.Metadata.defaults(0))
                .build(),
            new ClassifierBuilder()
                .condition(MATCH_BLUE)
                .action(PREDICTION_YELLOW)
                .metadata(Classifier.Metadata.defaults(0))
                .build());
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
            new ClassifierBuilder()
                .condition(MATCH_GREEN)
                .action(PREDICTION_RED)
                .metadata(Classifier.Metadata.defaults(0))
                .build(),
            new ClassifierBuilder()
                .condition(MATCH_BLUE)
                .action(PREDICTION_YELLOW)
                .metadata(Classifier.Metadata.defaults(0))
                .build());
    var sut = new MatchSetBuilder().addAllClassifiers(allRules).build();
    var expected =
        Map.of(
            PREDICTION_RED, List.of(allRules.get(0)), PREDICTION_YELLOW, List.of(allRules.get(1)));

    // when
    var result = sut.classifiersByPrediction();

    // then
    Assertions.assertThat(result).isEqualTo(expected);
  }

  @Test
  void groupsClassifiersByPredictionWithDuplicatePredictions() {
    // given
    var allRules =
        List.of(
            new ClassifierBuilder()
                .condition(MATCH_GREEN)
                .action(PREDICTION_YELLOW)
                .metadata(Classifier.Metadata.defaults(0))
                .build(),
            new ClassifierBuilder()
                .condition(MATCH_BLUE)
                .action(PREDICTION_YELLOW)
                .metadata(Classifier.Metadata.defaults(0))
                .build());
    var sut = new MatchSetBuilder().addAllClassifiers(allRules).build();
    var expected = Map.of(PREDICTION_YELLOW, allRules);

    // when
    var result = sut.classifiersByPrediction();

    // then
    Assertions.assertThat(result).isEqualTo(expected);
  }
}
