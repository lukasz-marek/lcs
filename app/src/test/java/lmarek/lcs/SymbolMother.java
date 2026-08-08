package lmarek.lcs;

import lmarek.lcs.classifier.rule.symbol.Symbol;
import lmarek.lcs.classifier.rule.symbol.SymbolFactory;

public final class SymbolMother {
  private SymbolMother() {}

  private static final SymbolFactory FACTORY = new SymbolFactory();

  public static Symbol symbol(String value) {
    return FACTORY.provideSymbolForValue(value);
  }
}
