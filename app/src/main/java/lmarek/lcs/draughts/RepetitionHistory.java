package lmarek.lcs.draughts;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** A persistent linked history. Appending a reversible position shares the earlier chain. */
public final class RepetitionHistory {
  private final PositionSignature signature;
  private final @Nullable RepetitionHistory previous;
  private final int currentOccurrences;
  private final int size;
  private final int hashCode;

  private RepetitionHistory(
      PositionSignature signature,
      @Nullable RepetitionHistory previous,
      int currentOccurrences,
      int size) {
    this.signature = Objects.requireNonNull(signature);
    this.previous = previous;
    this.currentOccurrences = currentOccurrences;
    this.size = size;
    this.hashCode = 31 * (previous == null ? 1 : previous.hashCode) + signature.hashCode();
  }

  public static RepetitionHistory start(PositionSignature signature) {
    return new RepetitionHistory(signature, null, 1, 1);
  }

  public RepetitionHistory append(PositionSignature next) {
    var occurrences = 1;
    for (@Nullable RepetitionHistory entry = this; entry != null; entry = entry.previous) {
      if (entry.signature.equals(next)) {
        occurrences++;
      }
    }
    return new RepetitionHistory(next, this, occurrences, size + 1);
  }

  public PositionSignature signature() {
    return signature;
  }

  public Optional<RepetitionHistory> previous() {
    return Optional.ofNullable(previous);
  }

  public int currentOccurrences() {
    return currentOccurrences;
  }

  public int size() {
    return size;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RepetitionHistory history)
        || size != history.size
        || hashCode != history.hashCode) {
      return false;
    }
    @Nullable RepetitionHistory left = this;
    @Nullable RepetitionHistory right = history;
    while (left != null && right != null) {
      if (!left.signature.equals(right.signature)) {
        return false;
      }
      left = left.previous;
      right = right.previous;
    }
    return left == null && right == null;
  }

  @Override
  public int hashCode() {
    return hashCode;
  }
}
