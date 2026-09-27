package lmarek.lcs.arena;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

@JCStressTest
@Outcome(
    id = {"0, 0", "1, 1"},
    expect = Expect.ACCEPTABLE,
    desc = "One complete boundary")
@Outcome(expect = Expect.FORBIDDEN, desc = "Torn progress bundle")
@State
public class ProgressPublicationStress {
  record Bundle(int board, int telemetry) {}

  private final ProgressPublication<Bundle> publication =
      new ProgressPublication<>(new Bundle(0, 0));

  @Actor
  public void publish() {
    publication.publish(new Bundle(1, 1));
  }

  @Actor
  public void read(II_Result result) {
    var bundle = publication.get();
    result.r1 = bundle.board();
    result.r2 = bundle.telemetry();
  }
}
