package lmarek.lcs.classifier.learning;

import java.util.List;
import lmarek.lcs.classifier.rule.Action;
import lmarek.lcs.classifier.rule.Classifier;
import lmarek.lcs.classifier.rule.ClassifierBuilder;
import lmarek.lcs.classifier.rule.Condition;
import lmarek.lcs.classifier.rule.Matcher;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ActionSetServiceTest {
  private static final Condition MATCH_GREEN = new Condition(Matcher.oneOf(Symbol.of("green")));
  private static final Condition MATCH_BLUE = new Condition(Matcher.oneOf(Symbol.of("blue")));
  private static final Action PREDICTION_RED = new Action(Symbol.of("red"));
  private static final Action PREDICTION_YELLOW = new Action(Symbol.of("yellow"));

  @Test
  void shouldSelectAllClassifiersForRequestedAction() {
    // given
    var firstMatching =
        new ClassifierBuilder()
            .condition(MATCH_GREEN)
            .action(PREDICTION_RED)
            .metadata(Classifier.Metadata.defaults(0))
            .build();
    var nonMatching =
        new ClassifierBuilder()
            .condition(MATCH_GREEN)
            .action(PREDICTION_YELLOW)
            .metadata(Classifier.Metadata.defaults(0))
            .build();
    var secondMatching =
        new ClassifierBuilder()
            .condition(MATCH_BLUE)
            .action(PREDICTION_RED)
            .metadata(Classifier.Metadata.defaults(0))
            .build();
    var allRules = List.of(firstMatching, nonMatching, secondMatching);
    var matchSet = new MatchSetBuilder().addAllClassifiers(allRules).build();
    var sut = new ActionSetService();

    // when
    var result = sut.selectAction(PREDICTION_RED, matchSet);

    // then
    Assertions.assertThat(result.action()).isEqualTo(PREDICTION_RED);
    Assertions.assertThat(result.classifiers())
        .containsExactlyInAnyOrder(firstMatching, secondMatching);
    Assertions.assertThat(matchSet.classifiers()).containsExactlyElementsOf(allRules);
  }

  @Test
  void shouldReturnEmptyActionSetWhenRequestedActionIsAbsent() {
    // given
    var classifier =
        new ClassifierBuilder()
            .condition(MATCH_GREEN)
            .action(PREDICTION_RED)
            .metadata(Classifier.Metadata.defaults(0))
            .build();
    var matchSet = new MatchSetBuilder().addClassifiers(classifier).build();
    var sut = new ActionSetService();

    // when
    var result = sut.selectAction(PREDICTION_YELLOW, matchSet);

    // then
    Assertions.assertThat(result.action()).isEqualTo(PREDICTION_YELLOW);
    Assertions.assertThat(result.classifiers()).isEmpty();
  }

  @Test
  void shouldReturnEmptyActionSetWhenMatchSetIsEmpty() {
    // given
    var matchSet = new MatchSetBuilder().build();
    var sut = new ActionSetService();

    // when
    var result = sut.selectAction(PREDICTION_RED, matchSet);

    // then
    Assertions.assertThat(result.action()).isEqualTo(PREDICTION_RED);
    Assertions.assertThat(result.classifiers()).isEmpty();
  }

  @Test
  void shouldSelectClassifiersForEqualAction() {
    // given
    var classifier =
        new ClassifierBuilder()
            .condition(MATCH_GREEN)
            .action(PREDICTION_RED)
            .metadata(Classifier.Metadata.defaults(0))
            .build();
    var matchSet = new MatchSetBuilder().addClassifiers(classifier).build();
    var action = new Action(Symbol.of("red"));
    var sut = new ActionSetService();

    // when
    var result = sut.selectAction(action, matchSet);

    // then
    Assertions.assertThat(result.action()).isEqualTo(action);
    Assertions.assertThat(result.classifiers()).containsExactly(classifier);
  }
}
