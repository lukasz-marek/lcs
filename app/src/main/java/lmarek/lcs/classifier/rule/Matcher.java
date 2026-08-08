package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.rule.symbol.Symbol;

public interface Matcher {
  boolean matches(Symbol tested);
}
