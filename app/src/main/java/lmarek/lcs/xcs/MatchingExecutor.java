package lmarek.lcs.xcs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/** Run-owned CPU pool. Only immutable candidate data may be read by the predicate. */
public final class MatchingExecutor implements AutoCloseable {
  private final int workers;
  private final int threshold;
  private final @Nullable ThreadPoolExecutor pool;

  public MatchingExecutor(int workers, int threshold) {
    if (workers < 1 || workers > 15 || threshold < 1) {
      throw new IllegalArgumentException(
          "Matching requires 1..15 workers and a positive threshold");
    }
    this.workers = workers;
    this.threshold = threshold;
    pool =
        workers == 1
            ? null
            : new ThreadPoolExecutor(
                workers,
                workers,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(workers),
                Thread.ofPlatform().name("xcs-match-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
  }

  public static MatchingExecutor sequential() {
    return new MatchingExecutor(1, 32_768);
  }

  <T> List<T> filter(List<T> candidates, Predicate<T> matches) {
    checkInterrupted();
    if (pool == null || candidates.size() < threshold) {
      return range(candidates, matches, 0, candidates.size());
    }
    var tasks = new ArrayList<JoinedTask<List<T>>>();
    boolean complete = false;
    try {
      int count = Math.min(workers, candidates.size());
      for (int i = 0; i < count; i++) {
        checkInterrupted();
        int from = (int) ((long) candidates.size() * i / count);
        int to = (int) ((long) candidates.size() * (i + 1) / count);
        var task = new JoinedTask<>(() -> range(candidates, matches, from, to));
        tasks.add(task);
        pool.execute(task);
      }
      var result = new ArrayList<T>();
      for (var task : tasks) result.addAll(task.get());
      checkInterrupted();
      complete = true;
      return result;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new CancellationException("Matching interrupted");
    } catch (ExecutionException exception) {
      throw new IllegalStateException("Matching worker failed", exception.getCause());
    } finally {
      if (!complete) tasks.forEach(task -> task.cancel(true));
      boolean interrupted = Thread.interrupted();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      try {
        for (var task : tasks) {
          while (task.exited.getCount() != 0) {
            try {
              if (!task.exited.await(
                  Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("Matching tasks did not exit within five seconds");
              }
            } catch (InterruptedException exception) {
              interrupted = true;
            }
          }
        }
      } finally {
        pool.purge();
        if (interrupted) Thread.currentThread().interrupt();
      }
    }
  }

  private static <T> List<T> range(List<T> candidates, Predicate<T> predicate, int from, int to) {
    var result = new ArrayList<T>();
    for (int i = from; i < to; i++) {
      if ((i - from) % 1024 == 0) checkInterrupted();
      var candidate = candidates.get(i);
      if (predicate.test(candidate)) result.add(candidate);
    }
    checkInterrupted();
    return result;
  }

  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted())
      throw new CancellationException("Matching interrupted");
  }

  @Override
  public void close() {
    if (pool == null) return;
    pool.shutdownNow();
    boolean interrupted = Thread.interrupted();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    try {
      while (!pool.isTerminated()) {
        try {
          if (!pool.awaitTermination(
              Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
            throw new IllegalStateException(
                "Matching executor did not terminate within five seconds");
          }
        } catch (InterruptedException exception) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  // Future cancellation can precede actual exit. The latch tracks execution, not Future state.
  private static final class JoinedTask<T> extends FutureTask<T> {
    private final CountDownLatch exited = new CountDownLatch(1);
    private boolean started;

    JoinedTask(Callable<T> callable) {
      super(callable);
    }

    @Override
    public void run() {
      synchronized (this) {
        if (isCancelled()) {
          exited.countDown();
          return;
        }
        started = true;
      }
      try {
        super.run();
      } finally {
        exited.countDown();
      }
    }

    @Override
    public synchronized boolean cancel(boolean interrupt) {
      boolean cancelled = super.cancel(interrupt);
      if (!started) exited.countDown();
      return cancelled;
    }
  }
}
