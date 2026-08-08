package lmarek.lcs;

import lmarek.lcs.classifier.symbol.Symbol;
import lmarek.lcs.classifier.symbol.SymbolFactory;

public final class SymbolMother {
  private SymbolMother() {}

  private static final SymbolFactory FACTORY = new SymbolFactory();

  public static Symbol symbol(String value) {
    return FACTORY.provideSymbolForValue(value);
  }
}
