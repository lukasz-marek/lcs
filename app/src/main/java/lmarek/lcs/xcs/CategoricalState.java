package lmarek.lcs.xcs;

import java.util.List;
import java.util.Objects;

/** A fixed-width categorical observation consumed by XCS. */
public record CategoricalState(List<String> values) {
  public CategoricalState {
    values = List.copyOf(values);
    values.forEach(Objects::requireNonNull);
    if (values.isEmpty()) {
      throw new IllegalArgumentException("An XCS observation must contain at least one attribute");
    }
  }

  public CategoricalState(String... values) {
    this(List.of(values));
  }
}
