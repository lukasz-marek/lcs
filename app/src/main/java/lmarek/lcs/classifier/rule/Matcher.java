package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.symbol.Symbol;

public interface Matcher {
  boolean matches(Symbol tested);

  static Matcher any() {
    return new Any();
  }

  static Matcher oneOf(Symbol... symbols) {
    return new OneOf(symbols);
  }
}
