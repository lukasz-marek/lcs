package lmarek.lcs.classifier.rule;

import java.util.Set;
import lmarek.lcs.classifier.rule.symbol.Symbol;

public record AnySymbolOf(Set<Symbol> allowedValues) implements Matcher {
  public AnySymbolOf {
    allowedValues = Set.copyOf(allowedValues);
  }

  public AnySymbolOf(Symbol... allowedValues) {
    this(Set.of(allowedValues));
  }

  @Override
  public boolean matches(Symbol tested) {
    return allowedValues().contains(tested);
  }
}
