package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ParallelLearningTest {
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 4, 8, 15, 16})
  void orderedUpdatesAndRepeatedRunsAreExact(int workers) {
    try (var executor = new MatchingExecutor(1, workers, 1)) {
      var expected = new ReferenceXcsPopulation(XcsParameters.defaults(), new SplittableRandom(91));
      var first = new XcsPopulation(XcsParameters.defaults(), new SplittableRandom(91));
      var second = new XcsPopulation(XcsParameters.defaults(), new SplittableRandom(91));
      first.matchingExecutor(executor);
      second.matchingExecutor(executor);
      var actions = java.util.stream.IntStream.range(0, 100).mapToObj(i -> "a" + i).toList();
      var state = new CategoricalState("s");
      var ids = first.match(state, actions, true, Set.of()).matchingRuleIds();
      second.match(state, actions, true, Set.of());
      expected.match(state, actions, true, Set.of());
      for (int step = 0; step < 60; step++) {
        double target = step % 3 == 0 ? 0 : step * .031;
        first.update(ids, target);
        second.update(ids, target);
        expected.update(ids, target);
        for (long id : ids) {
          assertThat(first.snapshot(id))
              .isEqualTo(expected.snapshot(id))
              .isEqualTo(second.snapshot(id));
          assertThat(first.ruleChanges(id))
              .isEqualTo(expected.ruleChanges(id))
              .isEqualTo(second.ruleChanges(id));
        }
        assertThat(first.telemetry()).isEqualTo(second.telemetry());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 4, 8, 15, 16})
  void cancelledCalculationDoesNotCommit(int workers) {
    try (var executor = new MatchingExecutor(1, workers, 1)) {
      var population = new XcsPopulation(XcsParameters.defaults(), new SplittableRandom(91));
      population.matchingExecutor(executor);
      var ids =
          population
              .match(new CategoricalState("s"), List.of("a", "b"), true, Set.of())
              .matchingRuleIds();
      var before = population.snapshot(ids.getFirst());
      var history = population.ruleChanges(ids.getFirst());
      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(() -> population.update(ids, 1))
            .isInstanceOf(CancellationException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
      assertThat(population.snapshot(ids.getFirst())).isEqualTo(before);
      assertThat(population.ruleChanges(ids.getFirst())).isEqualTo(history);
    }
  }
}
