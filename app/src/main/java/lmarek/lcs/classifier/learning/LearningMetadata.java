package lmarek.lcs.classifier.learning;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public interface LearningMetadata {
  long iteration();
}
