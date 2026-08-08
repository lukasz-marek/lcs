package lmarek.lcs.classifier.rule;

import static lmarek.lcs.SymbolMother.symbol;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnyOfTest {

  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void allowedValuesMatch(String tested) {
    // given
    var sut = new AnySymbolOf(symbol("red"), symbol("green"), symbol("blue"));
    // when
    var matches = sut.matches(symbol(tested));
    // then
    Assertions.assertThat(matches).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void nonAllowedValuesDoNotMatch(String tested) {
    // given
    var sut = new AnySymbolOf(symbol("black"), symbol("white"));
    // when
    var matches = sut.matches(symbol(tested));
    // then
    Assertions.assertThat(matches).isFalse();
  }
}
