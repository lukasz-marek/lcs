package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OneOfTest {

  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void allowedValuesMatch(String tested) {
    // given
    var sut = new OneOf(Symbol.of("red"), Symbol.of("green"), Symbol.of("blue"));
    // when
    var matches = sut.matches(Symbol.of(tested));
    // then
    Assertions.assertThat(matches).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void nonAllowedValuesDoNotMatch(String tested) {
    // given
    var sut = new OneOf(Symbol.of("black"), Symbol.of("white"));
    // when
    var matches = sut.matches(Symbol.of(tested));
    // then
    Assertions.assertThat(matches).isFalse();
  }
}
