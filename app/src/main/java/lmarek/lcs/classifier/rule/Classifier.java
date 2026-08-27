package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.data.SampleData;
import lmarek.lcs.classifier.symbol.Symbol;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public abstract class Classifier {
  abstract Condition condition();

  public abstract Symbol prediction();

  public boolean matches(SampleData sampleData) {
    return condition().matches(sampleData);
  }
}
