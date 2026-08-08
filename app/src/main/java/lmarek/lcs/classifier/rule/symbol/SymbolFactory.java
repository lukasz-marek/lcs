package lmarek.lcs.classifier.rule.symbol;

import com.google.common.collect.MapMaker;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SymbolFactory {
  private final Map<String, Symbol> symbolCache = new MapMaker().weakValues().makeMap();

  public Symbol provideSymbolForValue(String value) {
    return symbolCache.computeIfAbsent(value, ValuedSymbol::new);
  }
}
