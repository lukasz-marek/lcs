package lmarek.lcs.classifier.rule;

import java.util.ArrayList;
import java.util.List;
import lmarek.lcs.classifier.data.SampleData;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConditionTest {
  @Test
  void shouldKeepMatchingOriginalSampleWhenSourceListChanges() {
    // given
    var red = Symbol.of("red");
    var green = Symbol.of("green");
    var matchers = new ArrayList<Matcher>(List.of(Matcher.oneOf(red)));
    var sut = new Condition(matchers);

    // when
    matchers.set(0, Matcher.oneOf(green));

    // then
    Assertions.assertThat(sut.matches(new SampleData(red))).isTrue();
    Assertions.assertThat(sut.matches(new SampleData(green))).isFalse();
  }

  @Test
  void shouldNotAllowModifyingMatchers() {
    // given
    var matchers = new ArrayList<Matcher>(List.of(Matcher.any()));
    var sut = new Condition(matchers);

    // when / then
    Assertions.assertThatThrownBy(() -> sut.matchers().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
