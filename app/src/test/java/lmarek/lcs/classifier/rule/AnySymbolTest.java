package lmarek.lcs.classifier.rule;

import lmarek.lcs.SymbolMother;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnySymbolTest {
  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void allSymbolsMatch(String tested) {
    // given
    var sut = new AnySymbol();

    // when
    var matches = sut.matches(SymbolMother.symbol(tested));

    // then
    Assertions.assertThat(matches).isTrue();
  }
}
