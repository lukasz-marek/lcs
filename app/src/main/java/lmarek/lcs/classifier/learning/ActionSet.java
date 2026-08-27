package lmarek.lcs.classifier.learning;

import java.util.List;
import lmarek.lcs.classifier.rule.Action;
import lmarek.lcs.classifier.rule.Classifier;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public abstract class ActionSet {
  abstract Action action();

  abstract List<Classifier> classifiers();
}
