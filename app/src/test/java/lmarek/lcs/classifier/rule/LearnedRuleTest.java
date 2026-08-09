package lmarek.lcs.classifier.rule;

import static lmarek.lcs.SymbolMother.*;
import static lmarek.lcs.classifier.rule.Matcher.*;
import static lmarek.lcs.classifier.rule.Matcher.oneOf;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Stream;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
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
}
