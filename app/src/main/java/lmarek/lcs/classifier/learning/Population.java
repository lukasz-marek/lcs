package lmarek.lcs.classifier.learning;

import java.util.List;
import lmarek.lcs.classifier.data.Sample;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(
    overshadowImplementation = true,
    visibility = Value.Style.ImplementationVisibility.PRIVATE)
interface Population {
  List<Sample> samples();
}
