package lmarek.lcs.classifier.rule;

import java.util.List;
import lmarek.lcs.classifier.symbol.Symbol;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public abstract class Rule {
  abstract List<Matcher> matchers();

  abstract Symbol prediction();

  // todo
  public boolean matches(SampleData sampleData) {
    throw new UnsupportedOperationException();
  }
}
