package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class XcsPopulationTest {
  private static final CategoricalState STATE = new CategoricalState("value");

  @Test
  void crossoverReducesParentalMeanErrorAndFitness() {
    var population = new XcsPopulation(parameters(10, 0, 1.0, 1.0, 8.0, 2.0), new Random(4));
    population.advanceIteration();
    var match = population.match(STATE, List.of("a", "b"), true, Set.of());
    population.advanceIteration();

    population.runGeneticAlgorithm(match.matchingRuleIds(), STATE, match.legalActions(), Set.of());

    assertThat(population.snapshots())
        .filteredOn(rule -> rule.birthReason() == XcsBirthReason.GENETIC_ALGORITHM)
        .isNotEmpty()
        .allSatisfy(
            child -> {
              assertThat(child.predictionError()).isEqualTo(2.0);
              assertThat(child.fitness()).isEqualTo(0.2);
            });
  }

  @Test
  void noCrossoverCopiesParentErrorAndOnlyReducesFitness() {
    var population = new XcsPopulation(parameters(10, 0, 0.0, 1.0, 8.0, 2.0), new Random(5));
    population.advanceIteration();
    var match = population.match(STATE, List.of("only"), true, Set.of());
    population.advanceIteration();

    population.runGeneticAlgorithm(match.matchingRuleIds(), STATE, match.legalActions(), Set.of());

    assertThat(population.snapshots())
        .filteredOn(rule -> rule.birthReason() == XcsBirthReason.GENETIC_ALGORITHM)
        .allSatisfy(
            child -> {
              assertThat(child.predictionError()).isEqualTo(8.0);
              assertThat(child.fitness()).isEqualTo(0.2);
            });
  }

  @Test
  void identicalOffspringMergeIntoTheExistingMacroClassifier() {
    var population = new XcsPopulation(parameters(10, 0, 0.0, 0.0, 0.0, 0.01), new Random(6));
    population.advanceIteration();
    var match = population.match(STATE, List.of("only"), true, Set.of());
    population.advanceIteration();

    population.runGeneticAlgorithm(match.matchingRuleIds(), STATE, match.legalActions(), Set.of());

    assertThat(population.snapshots())
        .singleElement()
        .extracting(XcsRuleSnapshot::numerosity)
        .isEqualTo(3);
    assertThat(population.eventsAfter(0, 100))
        .filteredOn(event -> event.type() == XcsEvolutionEvent.Type.MERGED)
        .hasSize(2);
  }

  @Test
  void geneticAlgorithmRequiresElapsedTimeStrictlyGreaterThanThreshold() {
    var population = new XcsPopulation(parameters(10, 1, 0.0, 0.0, 0.0, 0.01), new Random(7));
    population.advanceIteration();
    var match = population.match(STATE, List.of("only"), true, Set.of());
    population.advanceIteration();

    population.runGeneticAlgorithm(match.matchingRuleIds(), STATE, match.legalActions(), Set.of());
    assertThat(population.eventsAfter(0, 100))
        .noneMatch(event -> event.type() == XcsEvolutionEvent.Type.OFFSPRING);

    population.advanceIteration();
    population.runGeneticAlgorithm(match.matchingRuleIds(), STATE, match.legalActions(), Set.of());
    assertThat(population.eventsAfter(0, 100))
        .filteredOn(event -> event.type() == XcsEvolutionEvent.Type.OFFSPRING)
        .hasSize(2);
  }

  @Test
  void coveringMayDeleteExcessNumerosityButKeepsAProtectedRuleId() {
    var population = new XcsPopulation(parameters(2, 0, 0.0, 0.0, 0.0, 0.01), new Random(8));
    population.advanceIteration();
    var match = population.match(STATE, List.of("a"), true, Set.of());
    long protectedId = match.matchingRuleIds().getFirst();
    population.advanceIteration();
    population.runGeneticAlgorithm(match.matchingRuleIds(), STATE, match.legalActions(), Set.of());
    assertThat(population.snapshot(protectedId).orElseThrow().numerosity()).isEqualTo(2);

    population.match(STATE, List.of("a", "b"), true, Set.of(protectedId));

    assertThat(population.snapshot(protectedId))
        .get()
        .extracting(XcsRuleSnapshot::numerosity)
        .isEqualTo(1);
    assertThat(population.snapshots())
        .hasSize(2)
        .allSatisfy(rule -> assertThat(rule.numerosity()).isOne());
  }

  @Test
  void rejectedMergeCannotDeleteItsRecipientBeforeIncrementingIt() {
    var population = new XcsPopulation(parameters(2, 0, 0.0, 0.0, 0.0, 0.01), new Random(9));
    population.advanceIteration();
    var match = population.match(STATE, List.of("a", "b"), true, Set.of());
    long targetId =
        population.snapshots().stream()
            .filter(rule -> rule.actionId().equals("a"))
            .findFirst()
            .orElseThrow()
            .id();
    long otherId =
        population.snapshots().stream()
            .filter(rule -> rule.actionId().equals("b"))
            .findFirst()
            .orElseThrow()
            .id();
    population.advanceIteration();

    population.runGeneticAlgorithm(List.of(targetId), STATE, match.legalActions(), Set.of(otherId));

    assertThat(population.snapshot(targetId)).isPresent();
    assertThat(population.snapshot(otherId)).isPresent();
    assertThat(population.snapshots()).hasSize(2);
    assertThat(population.eventsAfter(0, 100))
        .filteredOn(event -> event.type() == XcsEvolutionEvent.Type.CAPACITY_REJECTED)
        .hasSize(2);
  }

  private static XcsParameters parameters(
      int maximumPopulation,
      int gaThreshold,
      double crossover,
      double mutation,
      double initialError,
      double initialFitness) {
    var defaults = XcsParameters.defaults();
    return new XcsParameters(
        maximumPopulation,
        defaults.beta(),
        defaults.gamma(),
        0.0,
        defaults.accuracyAlpha(),
        defaults.errorThreshold(),
        defaults.accuracyPower(),
        0.0,
        4.0,
        initialError,
        initialFitness,
        gaThreshold,
        crossover,
        mutation,
        defaults.deletionExperienceThreshold(),
        defaults.deletionFitnessFraction(),
        defaults.subsumptionExperienceThreshold(),
        false);
  }
}
