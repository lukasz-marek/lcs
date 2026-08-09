package lmarek.lcs.classifier.rule;

import java.util.List;
import lmarek.lcs.classifier.symbol.Symbol;

public record SampleData(List<Symbol> values) {
  public SampleData {
    values = List.copyOf(values);
  }

  public SampleData(Symbol... values) {
    this(List.of(values));
  }
}
