package lmarek.lcs.classifier.rule;

import java.util.Set;
import lmarek.lcs.classifier.symbol.Symbol;

record OneOf(Set<Symbol> allowedValues) implements Matcher {
  public OneOf {
    allowedValues = Set.copyOf(allowedValues);
  }

  public OneOf(Symbol... allowedValues) {
    this(Set.of(allowedValues));
  }

  @Override
  public boolean matches(Symbol tested) {
    return allowedValues().contains(tested);
  }
}
