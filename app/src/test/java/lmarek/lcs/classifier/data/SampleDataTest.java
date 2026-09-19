package lmarek.lcs.classifier.data;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SampleDataTest {
  @Test
  void shouldKeepValuesWhenSourceListChanges() {
    // given
    var red = "red";
    var green = "green";
    var values = new ArrayList<>(List.of(red, green, red));
    var sut = new SampleData(values);

    // when
    values.clear();

    // then
    Assertions.assertThat(sut.values()).containsExactly(red, green, red);
  }

  @Test
  void shouldKeepValuesWhenSourceArrayChanges() {
    // given
    var red = "red";
    var green = "green";
    var values = new String[] {red, green, red};
    var sut = new SampleData(values);

    // when
    values[0] = "blue";

    // then
    Assertions.assertThat(sut.values()).containsExactly(red, green, red);
  }

  @Test
  void shouldNotAllowModifyingValues() {
    // given
    var values = new ArrayList<>(List.of("red"));
    var sut = new SampleData(values);

    // when / then
    Assertions.assertThatThrownBy(() -> sut.values().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
