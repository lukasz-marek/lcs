package lmarek.lcs.arena;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

@JCStressTest
@Outcome(id = "0", expect = Expect.ACCEPTABLE, desc = "Disconnect discards pending notification")
@Outcome(expect = Expect.FORBIDDEN, desc = "Notification retained after disconnect")
@State
public class SubscriberCloseStress {
  private final ArenaEventStream.Mailbox mailbox = new ArenaEventStream.Mailbox();

  @Actor
  public void enqueue() {
    mailbox.offer(new ArenaUpdate("run", 1));
  }

  @Actor
  public void close() {
    mailbox.disconnect();
  }

  @Arbiter
  public void check(I_Result result) {
    try {
      result.r1 = mailbox.take() == null ? 0 : 1;
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }
}
