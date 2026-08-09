package lmarek.lcs.classifier.symbol;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SymbolTest {
  @ParameterizedTest
  @ValueSource(strings = {"admin", "red", "common-lisp", "Java", "last_write_wins", "1", "0"})
  void canCreateValidSymbols(String validValue) {
    // when / then
    Assertions.assertThatCode(() -> Symbol.of(validValue)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", ",", "common lisp", "what a shame!", "   look"})
  void cannotCreateInvalidSymbols(String invalidValue) {
    // when / then
    Assertions.assertThatThrownBy(() -> Symbol.of(invalidValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(invalidValue);
  }

  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void symbolsWithSameValueAreCached(String value) {
    // given
    var first = Symbol.of(value);
    var second = Symbol.of(value);

    // then
    Assertions.assertThat(first).isSameAs(second);
    Assertions.assertThat(first).isEqualTo(second);
  }

  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void symbolsWithDifferentValueAreUnique(String value) {
    // given
    var first = Symbol.of(value);
    var second = Symbol.of(value + "-suffix");

    // then
    Assertions.assertThat(first).isNotSameAs(second);
    Assertions.assertThat(first).isNotEqualTo(second);
  }
}
