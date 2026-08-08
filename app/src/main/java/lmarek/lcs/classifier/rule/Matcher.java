package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.symbol.Symbol;

public interface Matcher {
  boolean matches(Symbol tested);
}
