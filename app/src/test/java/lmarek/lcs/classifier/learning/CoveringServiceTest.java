package lmarek.lcs.classifier.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import lmarek.lcs.classifier.data.SampleData;
import lmarek.lcs.classifier.rule.Action;
import lmarek.lcs.classifier.rule.Any;
import lmarek.lcs.classifier.rule.Classifier;
import lmarek.lcs.classifier.rule.OneOf;
import lmarek.lcs.classifier.symbol.Symbol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoveringServiceTest {
  private static final LearningMetadata LEARNING_METADATA =
      new LearningMetadataBuilder().iteration(21L).build();

  @Test
  void shouldGenerateExactRuleWhenGeneralizationIsDisabled() {
    // given
    var sample = new SampleData(Stream.of("I", "want", "exact", "match").map(Symbol::of).toList());
    var differentSample =
        new SampleData(Stream.of("different", "want", "exact", "match").map(Symbol::of).toList());
    var sut = new CoveringService(0);
    // when

    var rule = sut.generateClassifier(sample, new Action(Symbol.of("OK")), LEARNING_METADATA);

    // then
    assertThat(rule.matches(sample)).isTrue(); // ensure that logic is correct
    assertThat(rule.matches(differentSample)).isFalse();
    assertThat(rule.metadata().timestamp()).isEqualTo(LEARNING_METADATA.iteration());
    assertThat(rule.condition().matchers())
        .allSatisfy(matcher -> assertThat(matcher).isInstanceOf(OneOf.class));
  }

  @Test
  void shouldGenerateRuleMatchingAllWhenGeneralizationProbabilityIs100Percent() {
    // given
    var sample = new SampleData(Stream.of("I", "want", "exact", "match").map(Symbol::of).toList());
    var differentSample =
        new SampleData(Stream.of("another", "sample", "to", "match").map(Symbol::of).toList());
    var sut = new CoveringService(1);
    // when

    var rule = sut.generateClassifier(sample, new Action(Symbol.of("OK")), LEARNING_METADATA);

    // then
    assertThat(rule.matches(sample)).isTrue(); // ensure that logic is correct
    assertThat(rule.matches(differentSample)).isTrue();
    assertThat(rule.metadata().timestamp()).isEqualTo(LEARNING_METADATA.iteration());
    assertThat(rule.condition().matchers())
        .allSatisfy(matcher -> assertThat(matcher).isInstanceOf(Any.class));
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, 0.5, 1})
  void shouldGenerateMatchingRuleWithRequestedActionAndDefaultMetadata(double probability) {
    // given
    var sample = new SampleData(Symbol.of("red"), Symbol.of("green"), Symbol.of("blue"));
    var action = new Action(Symbol.of("OK"));
    var sut = new CoveringService(probability);

    // when
    var rule = sut.generateClassifier(sample, action, LEARNING_METADATA);

    // then
    assertThat(rule.matches(sample)).isTrue();
    assertThat(rule.action()).isEqualTo(action);
    assertThat(rule.metadata())
        .isEqualTo(Classifier.Metadata.defaults(LEARNING_METADATA.iteration()));
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1, -0.1, 1.001, 20, 100})
  void shouldThrowWhenProbabilityIsOutOfRange(double probability) {
    assertThatThrownBy(() -> new CoveringService(probability))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
  void shouldThrowWhenProbabilityIsNotFinite(double probability) {
    // when / then
    assertThatThrownBy(() -> new CoveringService(probability))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, 0.001, 0.1, 0.5, 0.9, 0.999, 1})
  void shouldAllowValidProbabilities(double probability) {
    assertThatCode(() -> new CoveringService(probability)).doesNotThrowAnyException();
  }
}
