package lmarek.lcs.classifier.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import lmarek.lcs.classifier.data.Sample;
import lmarek.lcs.classifier.data.SampleData;
import lmarek.lcs.classifier.symbol.Symbol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoveringRuleServiceTest {

  @Test
  void shouldGenerateExactRuleWhenGeneralizationIsDisabled() {
    // given
    var sample =
        new Sample(
            new SampleData(Stream.of("I", "want", "exact", "match").map(Symbol::of).toList()),
            Symbol.of("OK"));
    var sut = new CoveringRuleService(0);
    // when

    var rule = sut.generateCoveringRule(sample);

    // then
    assertThat(rule.matches(sample.data())).isTrue(); // ensure that logic is correct
    assertThat(rule.matchers())
        .allSatisfy(matcher -> assertThat(matcher).isInstanceOf(OneOf.class));
  }

  @Test
  void shouldGenerateRuleMatchingAllWhenGeneralizationProbabilityIs100Percent() {
    // given
    var sample =
        new Sample(
            new SampleData(Stream.of("I", "want", "exact", "match").map(Symbol::of).toList()),
            Symbol.of("OK"));
    var sut = new CoveringRuleService(1);
    // when

    var rule = sut.generateCoveringRule(sample);

    // then
    assertThat(rule.matches(sample.data())).isTrue(); // ensure that logic is correct
    assertThat(rule.matchers()).allSatisfy(matcher -> assertThat(matcher).isInstanceOf(Any.class));
  }

  @ParameterizedTest
  @ValueSource(doubles = {-1, -0.1, 1.001, 20, 100})
  void shouldThrowWhenProbabilityIsOutOfRange(double probability) {
    assertThatThrownBy(() -> new CoveringRuleService(probability))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, 0.001, 0.1, 0.5, 0.9, 0.999, 1})
  void shouldAllowValidProbabilities(double probability) {
    assertThatCode(() -> new CoveringRuleService(probability)).doesNotThrowAnyException();
  }
}
