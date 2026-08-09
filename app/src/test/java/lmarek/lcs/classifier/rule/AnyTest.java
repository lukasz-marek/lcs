package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnyTest {
  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void allSymbolsMatch(String tested) {
    // given
    var sut = new Any();

    // when
    var matches = sut.matches(Symbol.of(tested));

    // then
    Assertions.assertThat(matches).isTrue();
  }
}
