package lmarek.lcs.classifier.rule;

import java.util.HashSet;
import java.util.Set;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
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

  @Test
  void shouldKeepMatchingOriginalSymbolsWhenSourceSetChanges() {
    // given
    var red = Symbol.of("red");
    var green = Symbol.of("green");
    var allowedValues = new HashSet<>(Set.of(red));
    var sut = new OneOf(allowedValues);

    // when
    allowedValues.clear();
    allowedValues.add(green);

    // then
    Assertions.assertThat(sut.matches(red)).isTrue();
    Assertions.assertThat(sut.matches(green)).isFalse();
  }

  @Test
  void shouldKeepMatchingOriginalSymbolsWhenSourceArrayChanges() {
    // given
    var red = Symbol.of("red");
    var green = Symbol.of("green");
    var allowedValues = new Symbol[] {red};
    var sut = new OneOf(allowedValues);

    // when
    allowedValues[0] = green;

    // then
    Assertions.assertThat(sut.matches(red)).isTrue();
    Assertions.assertThat(sut.matches(green)).isFalse();
  }

  @Test
  void shouldNotAllowModifyingAllowedValues() {
    // given
    var allowedValues = new HashSet<>(Set.of(Symbol.of("red")));
    var sut = new OneOf(allowedValues);

    // when / then
    Assertions.assertThatThrownBy(() -> sut.allowedValues().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void shouldNotMatchWhenAllowedValuesAreEmpty() {
    // given
    var sut = new OneOf();

    // when
    var matches = sut.matches(Symbol.of("red"));

    // then
    Assertions.assertThat(matches).isFalse();
  }
}
