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
    return IntStream.range(0, matchers().size())
        .allMatch(index -> matchers().get(index).matches(sampleData.values().get(index)));
  }
}
