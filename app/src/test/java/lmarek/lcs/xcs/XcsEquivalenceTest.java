package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class XcsEquivalenceTest {
  @ParameterizedTest
  @ValueSource(ints = {1, 2, 4, 8, 15})
  void randomizedLearningExactlyMatchesReference(int workers) {
    for (int seed = 0; seed < 5; seed++) {
      var p =
          new XcsParameters(
              60, .2, .71, .5, .1, .01, 5, .4, .5, .01, .01, 0, .8, .4, 20, .1, 2, seed % 2 == 0);
      var reference = new ReferenceXcsPopulation(p, new SplittableRandom(seed));
      var actual = new XcsPopulation(p, new SplittableRandom(seed));
      try (var executor = new MatchingExecutor(workers, 1)) {
        actual.matchingExecutor(executor);
        var input = new Random(seed + 100);
        for (int step = 0; step < 300; step++) {
          var state = new CategoricalState("v" + input.nextInt(4), "v" + input.nextInt(4));
          var actions = step % 2 == 0 ? List.of("a", "b", "c") : List.of("b", "d");
          reference.advanceIteration();
          actual.advanceIteration();
          var expected = reference.match(state, actions, true, Set.of());
          var result = actual.match(state, actions, true, Set.of());
          assertThat(result.predictions()).isEqualTo(expected.predictions());
          assertThat(result.matchingRuleIds()).isEqualTo(expected.matchingRuleIds());
          assertThat(result.actionRuleIds()).isEqualTo(expected.actionRuleIds());
          var ids = expected.actionRuleIds().get(actions.get(input.nextInt(actions.size())));
          var target = input.nextDouble();
          reference.update(ids, target);
          actual.update(ids, target);
          var protectedIds = step % 3 == 0 ? Set.copyOf(ids) : Set.<Long>of();
          reference.runGeneticAlgorithm(ids, state, actions, protectedIds);
          actual.runGeneticAlgorithm(ids, state, actions, protectedIds);
          assertThat(actual.snapshots()).isEqualTo(reference.snapshots());
          assertThat(actual.eventsAfter(0, 2000)).isEqualTo(reference.eventsAfter(0, 2000));
          for (var rule : reference.snapshots()) {
            assertThat(actual.snapshot(rule.id())).contains(rule);
            assertThat(actual.ruleChanges(rule.id())).isEqualTo(reference.ruleChanges(rule.id()));
          }
          assertThat(actual.telemetry().get("xcs.population.micro"))
              .isEqualTo(reference.telemetry().get("xcs.population.micro"));
          for (var metric : List.of("xcs.fitness", "xcs.predictionError", "xcs.generality")) {
            assertThat(actual.telemetry().get(metric))
                .isCloseTo(
                    reference.telemetry().get(metric), org.assertj.core.data.Offset.offset(1e-12));
          }
          actual.verifyIndexes();
        }
        assertThat(actual.deepCopy(new SplittableRandom(9)).snapshots())
            .isEqualTo(reference.snapshots());
      }
    }
  }
}
