package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.symbol.Symbol;

public record AnySymbol() implements Matcher {
  @Override
  public boolean matches(Symbol tested) {
    return true;
  }
}
