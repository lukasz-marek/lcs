package lmarek.lcs.classifier.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lmarek.lcs.classifier.data.SampleData;
import lmarek.lcs.classifier.symbol.Symbol;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @Test
  void shouldKeepMatchingOriginalSampleWhenSourceArrayChanges() {
    // given
    var red = Symbol.of("red");
    var green = Symbol.of("green");
    var matchers = new Matcher[] {Matcher.oneOf(red)};
    var sut = new Condition(matchers);

    // when
    matchers[0] = Matcher.oneOf(green);

    // then
    Assertions.assertThat(sut.matches(new SampleData(red))).isTrue();
    Assertions.assertThat(sut.matches(new SampleData(green))).isFalse();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void shouldRejectSamplesWithDifferentAttributeCount(int sampleSize) {
    // given
    var sut = new Condition(Matcher.any());
    var sample = new SampleData(Collections.nCopies(sampleSize, Symbol.of("red")));

    // when / then
    Assertions.assertThatThrownBy(() -> sut.matches(sample))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldMatchEmptySampleWhenConditionIsEmpty() {
    // given
    var sut = new Condition();
    var sample = new SampleData();

    // when
    var matches = sut.matches(sample);

    // then
    Assertions.assertThat(matches).isTrue();
  }
}
