package lmarek.lcs.classifier.rule;

import java.util.Set;

public record OneOf(Set<String> allowedValues) implements Matcher {
  public OneOf {
    allowedValues = Set.copyOf(allowedValues);
  }

  public OneOf(String... allowedValues) {
    this(Set.of(allowedValues));
  }

  @Override
  public boolean matches(String tested) {
    return allowedValues().contains(tested);
  }
}
