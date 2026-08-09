package lmarek.lcs.classifier.rule;

import java.util.List;
import java.util.stream.IntStream;
import lmarek.lcs.classifier.symbol.Symbol;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public abstract class LearnedRule {
  abstract List<Matcher> matchers();

  public abstract Symbol prediction();

  public boolean matches(SampleData sampleData) {
    checkCompatibility(sampleData);
    return IntStream.range(0, matchers().size())
        .allMatch(index -> matchers().get(index).matches(sampleData.values().get(index)));
  }

  private void checkCompatibility(SampleData sampleData) {
    if (sampleData.values().size() != matchers().size()) {
      throw new IllegalArgumentException(
          "Cant match sample with %d attributes against rule with %d matchers"
              .formatted(sampleData.values().size(), matchers().size()));
    }
  }
}
