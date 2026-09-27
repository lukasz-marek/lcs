package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class MatchingExecutorTest {
  @Test
  void interruptionJoinsActualWorkerExitAndPreservesInterrupt() throws Exception {
    try (var matcher = new MatchingExecutor(2, 1)) {
      var entered = new CountDownLatch(2);
      var interruptedWorkers = new CountDownLatch(2);
      var release = new CountDownLatch(1);
      var exited = new CountDownLatch(1);
      var preserved = new AtomicBoolean();
      var failure = new AtomicReference<Throwable>();
      var coordinator =
          Thread.ofPlatform()
              .start(
                  () -> {
                    try {
                      matcher.filter(
                          List.of(1, 2),
                          value -> {
                            entered.countDown();
                            try {
                              release.await();
                            } catch (InterruptedException exception) {
                              interruptedWorkers.countDown();
                              boolean interrupted = false;
                              while (true) {
                                try {
                                  release.await();
                                  break;
                                } catch (InterruptedException again) {
                                  interrupted = true;
                                }
                              }
                              if (interrupted) Thread.currentThread().interrupt();
                            }
                            return true;
                          });
                    } catch (Throwable exception) {
                      failure.set(exception);
                    } finally {
                      preserved.set(Thread.currentThread().isInterrupted());
                      exited.countDown();
                    }
                  });
      assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
      coordinator.interrupt();
      assertThat(interruptedWorkers.await(3, TimeUnit.SECONDS)).isTrue();
      assertThat(exited.getCount()).isOne();
      release.countDown();
      assertThat(exited.await(3, TimeUnit.SECONDS)).isTrue();
      coordinator.join();
      assertThat(preserved.get()).isTrue();
      assertThat(failure.get()).isInstanceOf(java.util.concurrent.CancellationException.class);
      assertThat(matcher.filter(List.of(1, 2), value -> true)).containsExactly(1, 2);
    }
  }

  @Test
  void workerFailureCancelsAndJoinsSiblings() throws Exception {
    try (var matcher = new MatchingExecutor(2, 1)) {
      var secondStarted = new CountDownLatch(1);
      var secondExited = new CountDownLatch(1);
      assertThatThrownBy(
              () ->
                  matcher.filter(
                      List.of(1, 2),
                      value -> {
                        if (value == 1) {
                          try {
                            secondStarted.await();
                          } catch (InterruptedException exception) {
                            throw new AssertionError(exception);
                          }
                          throw new IllegalArgumentException("forced failure");
                        }
                        secondStarted.countDown();
                        try {
                          new CountDownLatch(1).await();
                        } catch (InterruptedException expected) {
                          Thread.currentThread().interrupt();
                        } finally {
                          secondExited.countDown();
                        }
                        return true;
                      }))
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("forced failure");
      assertThat(secondExited.getCount()).isZero();
    }
  }

  @Test
  void shutdownRejectsSubmissionWithoutLeakingTasks() {
    var matcher = new MatchingExecutor(2, 1);
    matcher.close();
    assertThatThrownBy(() -> matcher.filter(List.of(1, 2), value -> true))
        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
  }
}
