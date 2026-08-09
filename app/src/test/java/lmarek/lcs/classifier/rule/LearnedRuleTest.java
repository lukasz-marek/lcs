package lmarek.lcs.classifier.rule;

import static lmarek.lcs.classifier.rule.Matcher.any;
import static lmarek.lcs.classifier.rule.Matcher.oneOf;

import java.util.List;
import java.util.stream.Stream;
import lmarek.lcs.classifier.data.SampleData;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LearnedRuleTest {

  public static final Symbol PREDICTION = Symbol.of("white");

  static Stream<Arguments> matchingExamples() {
    var builder = Stream.<Arguments>builder();
    // use of any
    builder.accept(
        Arguments.of(
            List.of(any(), any(), any()),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(Symbol.of("red")), any(), any()),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(Symbol.of("red")), oneOf(Symbol.of("red")), any()),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(Symbol.of("red")), oneOf(Symbol.of("red")), oneOf(Symbol.of("red"))),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    // multiple symbols matched
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red")),
                oneOf(Symbol.of("red"))),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"))),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green"))),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    // mixed symbols in sample
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red")),
                oneOf(Symbol.of("red"))),
            List.of(Symbol.of("green"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"))),
            List.of(Symbol.of("green"), Symbol.of("green"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green"))),
            List.of(Symbol.of("green"), Symbol.of("green"), Symbol.of("green"))));
    return builder.build();
  }

  static Stream<Arguments> nonMatchingExamples() {
    var builder = Stream.<Arguments>builder();
    // use of any
    builder.accept(
        Arguments.of(
            List.of(any(), oneOf(Symbol.of("red")), oneOf(Symbol.of("green"))),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(Symbol.of("green")), any(), any()),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(Symbol.of("green")), oneOf(Symbol.of("red")), any()),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(Symbol.of("red")), oneOf(Symbol.of("green")), oneOf(Symbol.of("red"))),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(Symbol.of("red")), oneOf(Symbol.of("red")), oneOf(Symbol.of("green"))),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("red"))));
    // multiple symbols matched
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red")),
                oneOf(Symbol.of("red"))),
            List.of(Symbol.of("red"), Symbol.of("red"), Symbol.of("blue"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"))),
            List.of(Symbol.of("blue"), Symbol.of("blue"), Symbol.of("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green")),
                oneOf(Symbol.of("red"), Symbol.of("green"))),
            List.of(Symbol.of("blue"), Symbol.of("blue"), Symbol.of("blue"))));
    return builder.build();
  }

  @ParameterizedTest
  @MethodSource("matchingExamples")
  void shouldMatchSamples(List<Matcher> matchers, List<Symbol> sampleData) {
    // given
    var sut = new MatchableRuleBuilder().matchers(matchers).prediction(PREDICTION).build();
    var sample = new SampleData(sampleData);

    // when
    var matched = sut.matches(sample);

    // then
    Assertions.assertThat(matched).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonMatchingExamples")
  void shouldNotMatchSamples(List<Matcher> matchers, List<Symbol> sampleData) {
    // given
    var sut = new MatchableRuleBuilder().matchers(matchers).prediction(PREDICTION).build();
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
        new MatchableRuleBuilder().prediction(PREDICTION).addMatchers(any(), any(), any()).build();
    var sample = new SampleData(Symbol.of("red"));

    // when / then
    Assertions.assertThatThrownBy(() -> sut.matches(sample))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
