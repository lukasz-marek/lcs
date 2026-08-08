package lmarek.lcs.classifier.rule;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValuedSymbolTest {
  @ParameterizedTest
  @ValueSource(strings = {"admin", "red", "common-lisp", "Java", "last_write_wins", "1", "0"})
  void canCreateValidSymbols(String validValue) {
    // when / then
    Assertions.assertThatCode(() -> new ValuedSymbol(validValue)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", ",", "common lisp", "what a shame!", "   look"})
  void canCreateInvalidSymbols(String invalidValue) {
    // when / then
    Assertions.assertThatThrownBy(() -> new ValuedSymbol(invalidValue))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(invalidValue);
  }
}
