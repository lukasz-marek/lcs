package lmarek.lcs.classifier.rule.symbol;

import java.util.function.Predicate;
import java.util.regex.Pattern;

record ValuedSymbol(String value) implements Symbol {
  private static final String VALID_SYMBOL_PATTERN = "^[a-zA-Z0-9\\-_]+$";
  private static final Predicate<String> IS_VALID_SYMBOL =
      Pattern.compile(VALID_SYMBOL_PATTERN).asMatchPredicate();

  ValuedSymbol {
    if (!IS_VALID_SYMBOL.test(value)) {
      throw new IllegalArgumentException(
          "\"%s\" does not match \"%s\"".formatted(value, VALID_SYMBOL_PATTERN));
    }
  }
}
