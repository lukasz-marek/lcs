package lmarek.lcs.classifier.rule;

import lmarek.lcs.classifier.data.SampleData;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
public abstract class Classifier {
  public abstract Condition condition();

  public abstract Action action();

  public abstract Metadata metadata();

  public boolean matches(SampleData sampleData) {
    return condition().matches(sampleData);
  }

  @Value.Immutable
  @Value.Style(
      overshadowImplementation = true,
      visibility = Value.Style.ImplementationVisibility.PRIVATE)
  public abstract static class Metadata {
    @Value.Default.Double(10.0)
    public abstract double prediction();

    @Value.Default.Double(0.0)
    public abstract double predictionError();

    @Value.Default.Double(0.01)
    public abstract double fitness();

    @Value.Default.Long(0)
    public abstract long experience();

    @Value.Default.Long(1)
    public abstract long numerosity();

    @Value.Default.Int(1)
    public abstract int actionSetSize();

    public abstract long timestamp();

    public static Metadata defaults(long timestamp) {
      return new MetadataBuilder().timestamp(timestamp).build();
    }
  }
}
