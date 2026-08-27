package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.data.SampleData;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public abstract class Classifier {
  abstract Condition condition();

  public abstract Action action();

  public boolean matches(SampleData sampleData) {
    return condition().matches(sampleData);
  }
}
