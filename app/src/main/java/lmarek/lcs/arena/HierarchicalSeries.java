package lmarek.lcs.arena;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/** Bounded time series that combines old adjacent samples instead of simply discarding them. */
final class HierarchicalSeries {
  private static final int LEVELS = 32;
  private final int maximumPoints;
  private final List<Deque<Bucket>> levels = new ArrayList<>(LEVELS);
  private int size;

  HierarchicalSeries(int maximumPoints) {
    if (maximumPoints < 2) {
      throw new IllegalArgumentException("maximumPoints must be at least two");
    }
    this.maximumPoints = maximumPoints;
    for (int index = 0; index < LEVELS; index++) {
      levels.add(new ArrayDeque<>());
    }
  }

  synchronized void add(long x, double value) {
    levels.get(0).addLast(new Bucket(x, value, 1));
    size++;
    while (size > maximumPoints) {
      compactOldestPair();
    }
  }

  synchronized List<ChartPoint> points() {
    var result = new ArrayList<ChartPoint>(size);
    for (var level : levels) {
      for (var bucket : level) {
        result.add(new ChartPoint(bucket.x(), bucket.value()));
      }
    }
    result.sort(Comparator.comparingLong(ChartPoint::x));
    return List.copyOf(result);
  }

  private void compactOldestPair() {
    for (int levelIndex = 0; levelIndex < levels.size() - 1; levelIndex++) {
      var level = levels.get(levelIndex);
      if (level.size() >= 2) {
        var first = level.removeFirst();
        var second = level.removeFirst();
        long samples = first.samples() + second.samples();
        double average =
            (first.value() * first.samples() + second.value() * second.samples()) / samples;
        levels.get(levelIndex + 1).addLast(new Bucket(second.x(), average, samples));
        size--;
        return;
      }
    }
    throw new IllegalStateException("Unable to compact chart history");
  }

  private record Bucket(long x, double value, long samples) {}
}
