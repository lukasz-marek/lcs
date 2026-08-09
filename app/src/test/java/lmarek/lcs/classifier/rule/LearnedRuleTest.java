package lmarek.lcs.classifier.rule;

import static lmarek.lcs.SymbolMother.symbol;
import static lmarek.lcs.classifier.rule.Matcher.any;
import static lmarek.lcs.classifier.rule.Matcher.oneOf;

import java.util.List;
import java.util.stream.Stream;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LearnedRuleTest {

  public static final Symbol PREDICTION = symbol("white");

  static Stream<Arguments> matchingExamples() {
    var builder = Stream.<Arguments>builder();
    // use of any
    builder.accept(
        Arguments.of(
            List.of(any(), any(), any()), List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(symbol("red")), any(), any()),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(symbol("red")), oneOf(symbol("red")), any()),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(symbol("red")), oneOf(symbol("red")), oneOf(symbol("red"))),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    // multiple symbols matched
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")), oneOf(symbol("red")), oneOf(symbol("red"))),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"))),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green"))),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    // mixed symbols in sample
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")), oneOf(symbol("red")), oneOf(symbol("red"))),
            List.of(symbol("green"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"))),
            List.of(symbol("green"), symbol("green"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green"))),
            List.of(symbol("green"), symbol("green"), symbol("green"))));
    return builder.build();
  }

  static Stream<Arguments> nonMatchingExamples() {
    var builder = Stream.<Arguments>builder();
    // use of any
    builder.accept(
        Arguments.of(
            List.of(any(), oneOf(symbol("red")), oneOf(symbol("green"))),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(symbol("green")), any(), any()),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(symbol("green")), oneOf(symbol("red")), any()),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(symbol("red")), oneOf(symbol("green")), oneOf(symbol("red"))),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(oneOf(symbol("red")), oneOf(symbol("red")), oneOf(symbol("green"))),
            List.of(symbol("red"), symbol("red"), symbol("red"))));
    // multiple symbols matched
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")), oneOf(symbol("red")), oneOf(symbol("red"))),
            List.of(symbol("red"), symbol("red"), symbol("blue"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"))),
            List.of(symbol("blue"), symbol("blue"), symbol("red"))));
    builder.accept(
        Arguments.of(
            List.of(
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green")),
                oneOf(symbol("red"), symbol("green"))),
            List.of(symbol("blue"), symbol("blue"), symbol("blue"))));
    return builder.build();
  }

  @ParameterizedTest
  @MethodSource("matchingExamples")
  void shouldMatchSamples(List<Matcher> matchers, List<Symbol> sampleData) {
    // given
    var sut = new LearnedRuleBuilder().matchers(matchers).prediction(PREDICTION).build();
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
    var sut = new LearnedRuleBuilder().matchers(matchers).prediction(PREDICTION).build();
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
        new LearnedRuleBuilder().prediction(PREDICTION).addMatchers(any(), any(), any()).build();
    var sample = new SampleData(symbol("red"));

    // when / then
    Assertions.assertThatThrownBy(() -> sut.matches(sample))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
