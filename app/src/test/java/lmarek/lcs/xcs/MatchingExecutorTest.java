package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(10)
class MatchingExecutorTest {
  @org.junit.jupiter.api.Test
  void closingCompletesQueuedCalculations() throws Exception {
    var executor = new MatchingExecutor(1, 2, 1);
    var field = MatchingExecutor.class.getDeclaredField("pool");
    field.setAccessible(true);
    var pool = (java.util.concurrent.ThreadPoolExecutor) field.get(executor);
    var occupied = new CountDownLatch(2);
    for (int i = 0; i < 2; i++) {
      pool.execute(
          () -> {
            occupied.countDown();
            try {
              new CountDownLatch(1).await();
            } catch (InterruptedException expected) {
              Thread.currentThread().interrupt();
            }
          });
    }
    assertThat(occupied.await(3, TimeUnit.SECONDS)).isTrue();
    var failure = new AtomicReference<Throwable>();
    var coordinator =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    executor.calculate(2, value -> {});
                  } catch (Throwable problem) {
                    failure.set(problem);
                  }
                });
    try {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
      while (pool.getQueue().size() < 2 && System.nanoTime() < deadline) Thread.onSpinWait();
      assertThat(pool.getQueue()).hasSize(2);
    } finally {
      executor.close();
      coordinator.join(3000);
    }
    assertThat(coordinator.isAlive()).isFalse();
    assertThat(failure.get()).isInstanceOf(java.util.concurrent.CancellationException.class);
  }

  private static void calculate(
      MatchingExecutor executor, boolean learning, java.util.function.IntConsumer calculation) {
    if (learning) executor.calculate(2, calculation);
    else
      executor.filter(
          List.of(0, 1),
          value -> {
            calculation.accept(value);
            return true;
          });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void interruptionJoinsActualWorkerExitAndPreservesInterrupt(boolean learning) throws Exception {
    try (var matcher = new MatchingExecutor(2, 2, 1)) {
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
                      calculate(
                          matcher,
                          learning,
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void workerFailureCancelsAndJoinsSiblings(boolean learning) throws Exception {
    try (var matcher = new MatchingExecutor(2, 2, 1)) {
      var secondStarted = new CountDownLatch(1);
      var secondExited = new CountDownLatch(1);
      assertThatThrownBy(
              () ->
                  calculate(
                      matcher,
                      learning,
                      value -> {
                        if (value == 0) {
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
                      }))
          .isInstanceOf(IllegalStateException.class)
          .hasRootCauseMessage("forced failure");
      assertThat(secondExited.getCount()).isZero();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shutdownRejectsSubmissionWithoutLeakingTasks(boolean learning) {
    var matcher = new MatchingExecutor(2, 2, 1);
    matcher.close();
    assertThatThrownBy(() -> calculate(matcher, learning, value -> {}))
        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
  }
}
