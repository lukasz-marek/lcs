package lmarek.lcs.classifier.learning;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lmarek.lcs.classifier.rule.Action;
import lmarek.lcs.classifier.rule.Classifier;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public abstract class MatchSet {
  abstract List<Classifier> classifiers();

  Map<Action, Collection<Classifier>> classifiersByPrediction() {
    return classifiers().stream()
        .collect(groupingBy(Classifier::action, toCollection(ArrayList::new)));
  }
}
