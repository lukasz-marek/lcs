package lmarek.lcs.classifier.rule;

import java.util.List;
import java.util.stream.IntStream;
import lmarek.lcs.classifier.data.SampleData;

public record Condition(List<Matcher> matchers) {
  public Condition {
    matchers = List.copyOf(matchers);
  }

  public Condition(Matcher... matchers) {
    this(List.of(matchers));
  }

  public boolean matches(SampleData sampleData) {
    checkCompatibility(sampleData);
    return IntStream.range(0, matchers().size())
        .allMatch(index -> matchers().get(index).matches(sampleData.values().get(index)));
  }

  private void checkCompatibility(SampleData sampleData) {
    if (sampleData.values().size() != matchers().size()) {
      throw new IllegalArgumentException(
          "Cant match sample with %d attributes against rule with %d matchers"
              .formatted(sampleData.values().size(), matchers().size()));
    }
  }
}
