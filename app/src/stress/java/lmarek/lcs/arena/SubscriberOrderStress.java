package lmarek.lcs.arena;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

@JCStressTest
@Outcome(id = "2", expect = Expect.ACCEPTABLE, desc = "Newest revision retained")
@Outcome(expect = Expect.FORBIDDEN, desc = "Older revision replaced newer revision")
@State
public class SubscriberOrderStress {
  private final ArenaEventStream.Mailbox mailbox = new ArenaEventStream.Mailbox();

  @Actor
  public void older() {
    mailbox.offer(new ArenaUpdate("run", 1));
  }

  @Actor
  public void newer() {
    mailbox.offer(new ArenaUpdate("run", 2));
  }

  @Arbiter
  public void check(I_Result result) {
    mailbox.finish();
    try {
      var update = mailbox.take();
      result.r1 = update == null ? 0 : (int) update.revision();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }
}
