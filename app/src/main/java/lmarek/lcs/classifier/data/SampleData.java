package lmarek.lcs.classifier.data;

import java.util.List;

public record SampleData(List<String> values) {
  public SampleData {
    values = List.copyOf(values);
  }

  public SampleData(String... values) {
    this(List.of(values));
  }
}
