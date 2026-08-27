package lmarek.lcs.classifier.rule;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toCollection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lmarek.lcs.classifier.symbol.Symbol;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public interface MatchSet {
  List<MatchableRule> rules();

  default Map<Symbol, Collection<MatchableRule>> rulesByPrediction() {
    return rules().stream()
        .collect(groupingBy(MatchableRule::prediction, toCollection(ArrayList::new)));
  }
}
