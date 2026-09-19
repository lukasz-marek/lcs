package lmarek.lcs.classifier.rule;

import java.util.HashSet;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OneOfTest {

  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void allowedValuesMatch(String tested) {
    // given
    var sut = new OneOf("red", "green", "blue");
    // when
    var matches = sut.matches(tested);
    // then
    Assertions.assertThat(matches).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"red", "green", "blue"})
  void nonAllowedValuesDoNotMatch(String tested) {
    // given
    var sut = new OneOf("black", "white");
    // when
    var matches = sut.matches(tested);
    // then
    Assertions.assertThat(matches).isFalse();
  }

  @Test
  void shouldKeepMatchingOriginalValuesWhenSourceSetChanges() {
    // given
    var red = "red";
    var green = "green";
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
  void shouldKeepMatchingOriginalValuesWhenSourceArrayChanges() {
    // given
    var red = "red";
    var green = "green";
    var allowedValues = new String[] {red};
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
    var allowedValues = new HashSet<>(Set.of("red"));
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
    var matches = sut.matches("red");

    // then
    Assertions.assertThat(matches).isFalse();
  }

  @Test
  void shouldMatchEqualStringsWithDifferentReferences() {
    // given
    var sut = new OneOf("red");
    var tested = new String(new char[] {'r', 'e', 'd'});

    // when
    var matches = sut.matches(tested);

    // then
    Assertions.assertThat(tested).isNotSameAs("red");
    Assertions.assertThat(matches).isTrue();
  }
}
