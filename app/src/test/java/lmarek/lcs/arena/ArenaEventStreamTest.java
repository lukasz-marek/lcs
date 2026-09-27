package lmarek.lcs.arena;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class ArenaEventStreamTest {
  @Test
  void rejectsTheThirtyThirdSubscriber() {
    var stream = new ArenaEventStream();
    try {
      for (int index = 0; index < 32; index++) {
        stream.subscribe(new ArenaUpdate("run", index));
      }
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> stream.subscribe(new ArenaUpdate("run", 33)))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
          .hasMessageContaining("At most 32");
    } finally {
      stream.close();
    }
  }

  @Test
  void pendingRevisionsCoalesceAndTerminalDrainsBeforeClose() throws Exception {
    var mailbox = new ArenaEventStream.Mailbox();
    mailbox.offer(new ArenaUpdate("run", 2));
    mailbox.offer(new ArenaUpdate("run", 1));
    mailbox.offer(new ArenaUpdate("run", 3));
    mailbox.finish();
    mailbox.offer(new ArenaUpdate("run", 4));
    assertThat(mailbox.take()).isEqualTo(new ArenaUpdate("run", 3));
    assertThat(mailbox.take()).isNull();
  }

  @Test
  void enqueueDisconnectRaceCannotRetainPendingData() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < 1000; i++) {
        var mailbox = new ArenaEventStream.Mailbox();
        var barrier = new CyclicBarrier(2);
        var first =
            executor.submit(
                () -> {
                  barrier.await();
                  mailbox.offer(new ArenaUpdate("run", 1));
                  return null;
                });
        var second =
            executor.submit(
                () -> {
                  barrier.await();
                  mailbox.disconnect();
                  return null;
                });
        first.get();
        second.get();
        assertThat(mailbox.take()).isNull();
      }
    }
  }
}
