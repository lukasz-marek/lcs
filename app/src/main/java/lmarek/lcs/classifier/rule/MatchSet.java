package lmarek.lcs.classifier.rule;

import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@Value.Style(
        overshadowImplementation = true,
        visibility = Value.Style.ImplementationVisibility.PRIVATE)
public interface MatchSet {
    List<MatchableRule> rules();

}
