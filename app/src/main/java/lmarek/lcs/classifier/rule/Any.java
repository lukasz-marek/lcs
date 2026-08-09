package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.symbol.Symbol;

record Any() implements Matcher {
  @Override
  public boolean matches(Symbol tested) {
    return true;
  }
}
