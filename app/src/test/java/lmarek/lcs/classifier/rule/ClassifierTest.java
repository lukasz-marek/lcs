package lmarek.lcs.classifier.rule;

import static lmarek.lcs.classifier.rule.Matcher.any;
import static lmarek.lcs.classifier.rule.Matcher.oneOf;

import java.util.List;
import java.util.stream.Stream;
import lmarek.lcs.classifier.data.SampleData;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ClassifierTest {

  private static final Action ACTION = new Action("white");

  static Stream<Arguments> matchingExamples() {
    var builder = Stream.<Arguments>builder();
    // use of any
    builder.accept(Arguments.of(List.of(any(), any(), any()), List.of("red", "red", "red")));
    builder.accept(Arguments.of(List.of(oneOf("red"), any(), any()), List.of("red", "red", "red")));
    builder.accept(
        Arguments.of(List.of(oneOf("red"), oneOf("red"), any()), List.of("red", "red", "red")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red"), oneOf("red"), oneOf("red")), List.of("red", "red", "red")));
    // multiple values matched
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red"), oneOf("red")),
            List.of("red", "red", "red")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red", "green"), oneOf("red")),
            List.of("red", "red", "red")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red", "green"), oneOf("red", "green")),
            List.of("red", "red", "red")));
    // mixed values in sample
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red"), oneOf("red")),
            List.of("green", "red", "red")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red", "green"), oneOf("red")),
            List.of("green", "green", "red")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red", "green"), oneOf("red", "green")),
            List.of("green", "green", "green")));
    return builder.build();
  }

  static Stream<Arguments> nonMatchingExamples() {
    var builder = Stream.<Arguments>builder();
    // use of any
    builder.accept(
        Arguments.of(List.of(any(), oneOf("red"), oneOf("green")), List.of("red", "red", "red")));
    builder.accept(
        Arguments.of(List.of(oneOf("green"), any(), any()), List.of("red", "red", "red")));
    builder.accept(
        Arguments.of(List.of(oneOf("green"), oneOf("red"), any()), List.of("red", "red", "red")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red"), oneOf("green"), oneOf("red")), List.of("red", "red", "red")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red"), oneOf("red"), oneOf("green")), List.of("red", "red", "red")));
    // multiple values matched
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red"), oneOf("red")),
            List.of("red", "red", "blue")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red", "green"), oneOf("red")),
            List.of("blue", "blue", "red")));
    builder.accept(
        Arguments.of(
            List.of(oneOf("red", "green"), oneOf("red", "green"), oneOf("red", "green")),
            List.of("blue", "blue", "blue")));
    return builder.build();
  }

  @ParameterizedTest
  @MethodSource("matchingExamples")
  void shouldMatchSamples(List<Matcher> matchers, List<String> sampleData) {
    // given
    var sut =
        new ClassifierBuilder()
            .condition(new Condition(matchers))
            .action(ACTION)
            .metadata(Classifier.Metadata.defaults(0))
            .build();
    var sample = new SampleData(sampleData);

    // when
    var matched = sut.matches(sample);

    // then
    Assertions.assertThat(matched).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonMatchingExamples")
  void shouldNotMatchSamples(List<Matcher> matchers, List<String> sampleData) {
    // given
    var sut =
        new ClassifierBuilder()
            .condition(new Condition(matchers))
            .action(ACTION)
            .metadata(Classifier.Metadata.defaults(0))
            .build();
    var sample = new SampleData(sampleData);

    // when
    var matched = sut.matches(sample);

    // then
    Assertions.assertThat(matched).isFalse();
  }

  @Test
  void shouldThrowWhenSampleSizeIsDifferentThanRuleSize() {
    // given
    var sut =
        new ClassifierBuilder()
            .action(ACTION)
            .condition(new Condition(any(), any(), any()))
            .metadata(Classifier.Metadata.defaults(0))
            .build();
    var sample = new SampleData("red");

    // when / then
    Assertions.assertThatThrownBy(() -> sut.matches(sample))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
