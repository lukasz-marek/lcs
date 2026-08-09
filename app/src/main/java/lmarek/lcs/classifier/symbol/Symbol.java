package lmarek.lcs.classifier.symbol;

import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.immutables.value.Value;

@Value.Immutable(intern = true)
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE,
    weakInterning = true)
public abstract class Symbol {
  private static final String VALID_SYMBOL_PATTERN = "^[a-zA-Z0-9\\-_]+$";
  private static final Predicate<String> IS_VALID_SYMBOL =
      Pattern.compile(VALID_SYMBOL_PATTERN).asMatchPredicate();

  public abstract String value();

  public static Symbol of(String value) {
    return new SymbolBuilder().value(value).build();
  }

  @Value.Check
  void validate() {
    if (!IS_VALID_SYMBOL.test(value())) {
      throw new IllegalArgumentException(
          "\"%s\" does not match \"%s\"".formatted(value(), VALID_SYMBOL_PATTERN));
    }
  }
}
