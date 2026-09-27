package lmarek.lcs.xcs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Mutable XCS population. All randomness enters through the constructor. */
final class ReferenceXcsPopulation {
  private static final int RETAINED_EVENTS = 2_000;
  private static final int RETAINED_RULE_CHANGES = 50;

  private final XcsParameters parameters;
  private final RandomGenerator random;
  private final List<Rule> rules;
  private final Deque<XcsEvolutionEvent> events;
  private final Map<Long, Deque<XcsEvolutionEvent>> changesByRule;
  private long nextRuleId;
  private long nextEventSequence;
  private long iteration;
  private long coveringCount;
  private long gaCount;

  ReferenceXcsPopulation(XcsParameters parameters, RandomGenerator random) {
    this(
        parameters,
        random,
        new ArrayList<>(),
        new ArrayDeque<>(),
        new LinkedHashMap<>(),
        1,
        1,
        0,
        0,
        0);
  }

  private ReferenceXcsPopulation(
      XcsParameters parameters,
      RandomGenerator random,
      List<Rule> rules,
      Deque<XcsEvolutionEvent> events,
      Map<Long, Deque<XcsEvolutionEvent>> changesByRule,
      long nextRuleId,
      long nextEventSequence,
      long iteration,
      long coveringCount,
      long gaCount) {
    this.parameters = parameters;
    this.random = random;
    this.rules = rules;
    this.events = events;
    this.changesByRule = changesByRule;
    this.nextRuleId = nextRuleId;
    this.nextEventSequence = nextEventSequence;
    this.iteration = iteration;
    this.coveringCount = coveringCount;
    this.gaCount = gaCount;
  }

  ReferenceXcsPopulation deepCopy(RandomGenerator copyRandom) {
    var copiedRules = new ArrayList<Rule>(rules.size());
    rules.forEach(rule -> copiedRules.add(rule.copy()));
    var copiedChanges = new LinkedHashMap<Long, Deque<XcsEvolutionEvent>>();
    changesByRule.forEach(
        (ruleId, changes) -> copiedChanges.put(ruleId, new ArrayDeque<>(changes)));
    return new ReferenceXcsPopulation(
        parameters,
        copyRandom,
        copiedRules,
        new ArrayDeque<>(events),
        copiedChanges,
        nextRuleId,
        nextEventSequence,
        iteration,
        coveringCount,
        gaCount);
  }

  long advanceIteration() {
    return ++iteration;
  }

  MatchResult match(
      CategoricalState state,
      List<String> legalActionIds,
      boolean cover,
      Set<Long> protectedRuleIds) {
    var legal = new LinkedHashSet<>(legalActionIds);
    if (legal.size() != legalActionIds.size()) {
      throw new IllegalArgumentException("Legal action IDs must be unique");
    }
    if (legal.isEmpty()) {
      throw new IllegalArgumentException("XCS needs at least one legal action");
    }

    var matching = matchingRules(state, legal);
    var represented = new HashSet<String>();
    matching.forEach(rule -> represented.add(rule.actionId));
    var missing = legal.stream().filter(action -> !represented.contains(action)).toList();
    var unknown = cover ? List.<String>of() : missing;
    if (cover && !missing.isEmpty()) {
      var protectedForCovering = new HashSet<>(protectedRuleIds);
      matching.forEach(rule -> protectedForCovering.add(rule.id));
      reserveCoveringSpace(missing.size(), protectedForCovering);
      for (var action : missing) {
        var rule = coveredRule(state, action);
        rules.add(rule);
        matching.add(rule);
        coveringCount++;
        recordEvent(
            XcsEvolutionEvent.Type.COVERED,
            rule.id,
            rule.parentIds,
            "Covered an action that had no matching classifier: " + action);
      }
    }

    var predictions = new LinkedHashMap<String, Double>();
    var actionRuleIds = new LinkedHashMap<String, List<Long>>();
    for (var action : legal) {
      var actionRules = matching.stream().filter(rule -> rule.actionId.equals(action)).toList();
      var fitnessSum = actionRules.stream().mapToDouble(rule -> rule.fitness).sum();
      var prediction =
          fitnessSum > 0.0
              ? actionRules.stream().mapToDouble(rule -> rule.prediction * rule.fitness).sum()
                  / fitnessSum
              : 0.0;
      predictions.put(action, prediction);
      actionRuleIds.put(action, actionRules.stream().map(rule -> rule.id).toList());
    }
    return new MatchResult(
        state,
        List.copyOf(legal),
        predictions,
        unknown,
        matching.stream().map(rule -> rule.id).toList(),
        actionRuleIds);
  }

  void update(Collection<Long> actionSetRuleIds, double target) {
    if (!Double.isFinite(target)) {
      throw new IllegalArgumentException("The XCS update target must be finite");
    }
    var actionSet = findRules(actionSetRuleIds);
    if (actionSet.isEmpty()) {
      return;
    }
    var before = new LinkedHashMap<Rule, RuleMetrics>();
    actionSet.forEach(rule -> before.put(rule, RuleMetrics.from(rule)));
    var actionSetNumerosity = actionSet.stream().mapToInt(rule -> rule.numerosity).sum();
    for (var rule : actionSet) {
      var oldPrediction = rule.prediction;
      var absoluteError = Math.abs(target - oldPrediction);
      rule.experience++;
      var learningRate =
          rule.experience < 1.0 / parameters.beta() ? 1.0 / rule.experience : parameters.beta();
      // Wilson's ordering matters: error is based on the prediction before this update.
      rule.predictionError += learningRate * (absoluteError - rule.predictionError);
      rule.prediction += learningRate * (target - oldPrediction);
      rule.actionSetSize += learningRate * (actionSetNumerosity - rule.actionSetSize);
    }

    var accuracies = new LinkedHashMap<Rule, Double>();
    var accuracySum = 0.0;
    for (var rule : actionSet) {
      var accuracy =
          rule.predictionError < parameters.errorThreshold()
              ? 1.0
              : parameters.accuracyAlpha()
                  * Math.pow(
                      rule.predictionError / parameters.errorThreshold(),
                      -parameters.accuracyPower());
      accuracies.put(rule, accuracy);
      accuracySum += accuracy * rule.numerosity;
    }
    if (accuracySum > 0.0) {
      for (var rule : actionSet) {
        var relativeAccuracy = accuracies.getOrDefault(rule, 0.0) * rule.numerosity / accuracySum;
        rule.fitness += parameters.beta() * (relativeAccuracy - rule.fitness);
      }
    }
    for (var rule : actionSet) {
      var old = before.get(rule);
      if (old != null) {
        recordRuleChange(
            XcsEvolutionEvent.Type.UPDATED,
            rule.id,
            rule.parentIds,
            "prediction %s -> %s; error %s -> %s; fitness %s -> %s; experience %d -> %d; numerosity %d; action-set size %s -> %s"
                .formatted(
                    old.prediction,
                    rule.prediction,
                    old.predictionError,
                    rule.predictionError,
                    old.fitness,
                    rule.fitness,
                    old.experience,
                    rule.experience,
                    rule.numerosity,
                    old.actionSetSize,
                    rule.actionSetSize));
      }
    }
  }

  void runGeneticAlgorithm(
      Collection<Long> actionSetRuleIds,
      CategoricalState nicheState,
      List<String> nicheLegalActions,
      Set<Long> protectedRuleIds) {
    var actionSet = findRules(actionSetRuleIds);
    if (actionSet.isEmpty()) {
      return;
    }
    var numerosity = actionSet.stream().mapToInt(rule -> rule.numerosity).sum();
    var averageTimestamp =
        actionSet.stream().mapToDouble(rule -> rule.timestamp * rule.numerosity).sum() / numerosity;
    if (iteration - averageTimestamp <= parameters.gaThreshold()) {
      return;
    }
    actionSet.forEach(rule -> rule.timestamp = iteration);
    gaCount++;

    var parentOne = selectParent(actionSet);
    var parentTwo = selectParent(actionSet);
    var parents = List.of(parentOne.id, parentTwo.id);
    var childOne = offspring(parentOne, parents);
    var childTwo = offspring(parentTwo, parents);
    if (random.nextDouble() < parameters.crossoverProbability()) {
      crossover(childOne, childTwo);
      var meanPrediction = (parentOne.prediction + parentTwo.prediction) / 2.0;
      var reducedMeanError = (parentOne.predictionError + parentTwo.predictionError) * 0.5 * 0.25;
      var reducedMeanFitness = (parentOne.fitness + parentTwo.fitness) * 0.05;
      childOne.prediction = meanPrediction;
      childTwo.prediction = meanPrediction;
      childOne.predictionError = reducedMeanError;
      childTwo.predictionError = reducedMeanError;
      childOne.fitness = reducedMeanFitness;
      childTwo.fitness = reducedMeanFitness;
    }
    mutate(childOne, nicheState, nicheLegalActions);
    mutate(childTwo, nicheState, nicheLegalActions);
    insertOffspring(childOne, parentOne, parentTwo, protectedRuleIds);
    insertOffspring(childTwo, parentOne, parentTwo, protectedRuleIds);
  }

  List<XcsRuleSnapshot> snapshots() {
    return rules.stream()
        .sorted(Comparator.comparingLong(rule -> rule.id))
        .map(Rule::snapshot)
        .toList();
  }

  Optional<XcsRuleSnapshot> snapshot(long ruleId) {
    return rules.stream().filter(rule -> rule.id == ruleId).findFirst().map(Rule::snapshot);
  }

  List<XcsEvolutionEvent> eventsAfter(long sequence, int limit) {
    if (limit < 1) {
      return List.of();
    }
    return events.stream().filter(event -> event.sequence() > sequence).limit(limit).toList();
  }

  List<XcsEvolutionEvent> ruleChanges(long ruleId) {
    var changes = changesByRule.get(ruleId);
    return changes == null ? List.of() : List.copyOf(changes);
  }

  Map<String, Double> telemetry() {
    var macroCount = rules.size();
    var microCount = microPopulationSize();
    var averageGenerality = rules.stream().mapToDouble(Rule::generality).average().orElse(0.0);
    var averageError =
        rules.stream().mapToDouble(rule -> rule.predictionError).average().orElse(0.0);
    var averageFitness = rules.stream().mapToDouble(rule -> rule.fitness).average().orElse(0.0);
    return Map.of(
        "xcs.population.macro", (double) macroCount,
        "xcs.population.micro", (double) microCount,
        "xcs.generality", averageGenerality,
        "xcs.predictionError", averageError,
        "xcs.fitness", averageFitness,
        "xcs.covering", (double) coveringCount,
        "xcs.ga", (double) gaCount);
  }

  private List<Rule> matchingRules(CategoricalState state, Set<String> legalActions) {
    var result = new ArrayList<Rule>();
    for (var rule : rules) {
      if (legalActions.contains(rule.actionId) && rule.matches(state)) {
        result.add(rule);
      }
    }
    return result;
  }

  private Rule coveredRule(CategoricalState state, String action) {
    var condition = new ArrayList<Term>(state.values().size());
    for (var value : state.values()) {
      condition.add(
          random.nextDouble() < parameters.wildcardProbability()
              ? Term.wildcardTerm()
              : Term.exact(value));
    }
    return new Rule(
        nextRuleId++,
        condition,
        action,
        parameters.initialPrediction(),
        parameters.initialPredictionError(),
        parameters.initialFitness(),
        0,
        1,
        1.0,
        iteration,
        List.of(),
        XcsBirthReason.COVERING);
  }

  private Rule offspring(Rule parent, List<Long> parentIds) {
    // Without crossover, conventional XCS copies prediction/error and reduces fitness only.
    var child =
        new Rule(
            nextRuleId++,
            new ArrayList<>(parent.condition),
            parent.actionId,
            parent.prediction,
            parent.predictionError,
            parent.fitness * 0.1,
            0,
            1,
            parent.actionSetSize,
            iteration,
            parentIds,
            XcsBirthReason.GENETIC_ALGORITHM);
    recordEvent(
        XcsEvolutionEvent.Type.OFFSPRING,
        child.id,
        child.parentIds,
        "Created a GA offspring candidate");
    return child;
  }

  private void crossover(Rule first, Rule second) {
    var size = first.condition.size();
    if (size == 0) {
      return;
    }
    var firstCut = random.nextInt(size + 1);
    var secondCut = random.nextInt(size + 1);
    while (secondCut == firstCut) {
      secondCut = random.nextInt(size + 1);
    }
    if (firstCut > secondCut) {
      var temporary = firstCut;
      firstCut = secondCut;
      secondCut = temporary;
    }
    for (var index = firstCut; index < secondCut; index++) {
      var temporary = first.condition.get(index);
      first.condition.set(index, second.condition.get(index));
      second.condition.set(index, temporary);
    }
  }

  private void mutate(Rule child, CategoricalState state, List<String> legalActions) {
    var changes = new ArrayList<String>();
    for (var index = 0; index < child.condition.size(); index++) {
      if (random.nextDouble() < parameters.mutationProbability()) {
        var current = child.condition.get(index);
        child.condition.set(
            index, current.wildcard ? Term.exact(state.values().get(index)) : Term.wildcardTerm());
        changes.add("condition[" + index + "]");
      }
    }
    if (random.nextDouble() < parameters.mutationProbability()) {
      var alternatives =
          legalActions.stream().filter(action -> !action.equals(child.actionId)).toList();
      if (!alternatives.isEmpty()) {
        child.actionId = alternatives.get(random.nextInt(alternatives.size()));
        changes.add("action");
      }
    }
    if (!changes.isEmpty()) {
      recordEvent(
          XcsEvolutionEvent.Type.MUTATED,
          child.id,
          child.parentIds,
          "Mutation candidate changed " + String.join(", ", changes));
    }
  }

  private void insertOffspring(
      Rule child, Rule parentOne, Rule parentTwo, Set<Long> protectedRuleIds) {
    if (parameters.gaSubsumption()) {
      var possible = new ArrayList<Rule>();
      if (parentOne.subsumes(child, parameters)) {
        possible.add(parentOne);
      }
      if (parentTwo != parentOne && parentTwo.subsumes(child, parameters)) {
        possible.add(parentTwo);
      }
      if (!possible.isEmpty()) {
        var subsumer = possible.get(random.nextInt(possible.size()));
        if (makeRoomForOne(protectRecipient(protectedRuleIds, subsumer.id))) {
          subsumer.numerosity++;
          recordEvent(
              XcsEvolutionEvent.Type.SUBSUMED,
              subsumer.id,
              child.parentIds,
              "Rule subsumed offspring " + child.id + "; numerosity is now " + subsumer.numerosity);
          changesByRule.remove(child.id);
        } else {
          recordEvent(
              XcsEvolutionEvent.Type.CAPACITY_REJECTED,
              child.id,
              child.parentIds,
              "Offspring was discarded because every resident rule was active or pending");
          changesByRule.remove(child.id);
        }
        return;
      }
    }
    var identical = rules.stream().filter(resident -> resident.sameStructure(child)).findFirst();
    if (identical.isPresent()) {
      var resident = identical.orElseThrow();
      if (makeRoomForOne(protectRecipient(protectedRuleIds, resident.id))) {
        resident.numerosity++;
        recordEvent(
            XcsEvolutionEvent.Type.MERGED,
            resident.id,
            child.parentIds,
            "Merged identical offspring "
                + child.id
                + "; numerosity is now "
                + resident.numerosity);
        changesByRule.remove(child.id);
      } else {
        rejectOffspring(child);
      }
      return;
    }
    if (makeRoomForOne(protectedRuleIds)) {
      rules.add(child);
    } else {
      rejectOffspring(child);
    }
  }

  private boolean makeRoomForOne(Set<Long> protectedRuleIds) {
    if (microPopulationSize() < parameters.maximumPopulation()) {
      return true;
    }
    var deletable = rules.stream().anyMatch(rule -> isDeletable(rule, protectedRuleIds));
    if (!deletable) {
      return false;
    }
    deleteOne(protectedRuleIds);
    return true;
  }

  private void reserveCoveringSpace(int amount, Set<Long> protectedRuleIds) {
    var needed = Math.max(0, microPopulationSize() + amount - parameters.maximumPopulation());
    var deletable =
        rules.stream()
            .mapToInt(
                rule ->
                    protectedRuleIds.contains(rule.id)
                        ? Math.max(0, rule.numerosity - 1)
                        : rule.numerosity)
            .sum();
    if (needed > deletable) {
      throw capacityException(amount, protectedRuleIds);
    }
    for (var count = 0; count < needed; count++) {
      deleteOne(protectedRuleIds);
    }
  }

  private XcsCapacityException capacityException(int missingActions, Set<Long> protectedRuleIds) {
    var message =
        "XCS population cap %d cannot safely cover %d legal action(s); %d micro-classifiers are protected by active or pending decisions"
            .formatted(
                parameters.maximumPopulation(),
                missingActions,
                protectedMicroCount(protectedRuleIds));
    recordEvent(XcsEvolutionEvent.Type.CAPACITY_REJECTED, 0, List.of(), message);
    return new XcsCapacityException(message);
  }

  private int protectedMicroCount(Set<Long> protectedRuleIds) {
    return (int) rules.stream().filter(rule -> protectedRuleIds.contains(rule.id)).count();
  }

  private void rejectOffspring(Rule child) {
    recordEvent(
        XcsEvolutionEvent.Type.CAPACITY_REJECTED,
        child.id,
        child.parentIds,
        "Offspring was discarded because every resident rule was active or pending");
    changesByRule.remove(child.id);
  }

  private void deleteOne(Set<Long> protectedRuleIds) {
    var candidates = rules.stream().filter(rule -> isDeletable(rule, protectedRuleIds)).toList();
    if (candidates.isEmpty()) {
      throw capacityException(1, protectedRuleIds);
    }
    var populationFitness = rules.stream().mapToDouble(rule -> rule.fitness).sum();
    var populationSize = microPopulationSize();
    var averageFitness = populationSize == 0 ? 0.0 : populationFitness / populationSize;
    var votes = new double[candidates.size()];
    var voteSum = 0.0;
    for (var index = 0; index < candidates.size(); index++) {
      var rule = candidates.get(index);
      var vote = rule.actionSetSize * rule.numerosity;
      var fitnessPerMicro = rule.fitness / rule.numerosity;
      if (rule.experience > parameters.deletionExperienceThreshold()
          && fitnessPerMicro < parameters.deletionFitnessFraction() * averageFitness
          && fitnessPerMicro > 0.0) {
        vote *= averageFitness / fitnessPerMicro;
      }
      votes[index] = vote;
      voteSum += vote;
    }
    var selected =
        voteSum > 0.0
            ? candidates.get(roulette(votes, voteSum))
            : candidates.get(random.nextInt(candidates.size()));
    selected.numerosity--;
    if (selected.numerosity == 0) {
      rules.remove(selected);
    }
    recordEvent(
        XcsEvolutionEvent.Type.DELETED,
        selected.id,
        selected.parentIds,
        selected.numerosity == 0
            ? "Deleted the macro-classifier"
            : "Reduced numerosity to " + selected.numerosity);
    if (selected.numerosity == 0) {
      changesByRule.remove(selected.id);
    }
  }

  private static boolean isDeletable(Rule rule, Set<Long> protectedRuleIds) {
    return !protectedRuleIds.contains(rule.id) || rule.numerosity > 1;
  }

  private static Set<Long> protectRecipient(Set<Long> protectedRuleIds, long recipientId) {
    var protectedWithRecipient = new HashSet<>(protectedRuleIds);
    protectedWithRecipient.add(recipientId);
    return protectedWithRecipient;
  }

  private Rule selectParent(List<Rule> actionSet) {
    var fitnessSum = actionSet.stream().mapToDouble(rule -> rule.fitness).sum();
    if (fitnessSum <= 0.0) {
      return actionSet.get(random.nextInt(actionSet.size()));
    }
    var choice = random.nextDouble() * fitnessSum;
    var running = 0.0;
    for (var rule : actionSet) {
      running += rule.fitness;
      if (running >= choice) {
        return rule;
      }
    }
    return actionSet.get(actionSet.size() - 1);
  }

  private int roulette(double[] weights, double sum) {
    var choice = random.nextDouble() * sum;
    var running = 0.0;
    for (var index = 0; index < weights.length; index++) {
      running += weights[index];
      if (running >= choice) {
        return index;
      }
    }
    return weights.length - 1;
  }

  private List<Rule> findRules(Collection<Long> ids) {
    var requested = new HashSet<>(ids);
    return rules.stream().filter(rule -> requested.contains(rule.id)).toList();
  }

  private int microPopulationSize() {
    return rules.stream().mapToInt(rule -> rule.numerosity).sum();
  }

  private void recordEvent(
      XcsEvolutionEvent.Type type, long ruleId, List<Long> parentIds, String detail) {
    var event =
        new XcsEvolutionEvent(nextEventSequence++, iteration, type, ruleId, parentIds, detail);
    events.addLast(event);
    while (events.size() > RETAINED_EVENTS) {
      events.removeFirst();
    }
    if (ruleId > 0) {
      retainRuleChange(ruleId, event);
    }
  }

  private void recordRuleChange(
      XcsEvolutionEvent.Type type, long ruleId, List<Long> parentIds, String detail) {
    var event =
        new XcsEvolutionEvent(nextEventSequence++, iteration, type, ruleId, parentIds, detail);
    retainRuleChange(ruleId, event);
  }

  private void retainRuleChange(long ruleId, XcsEvolutionEvent event) {
    var changes = changesByRule.computeIfAbsent(ruleId, ignored -> new ArrayDeque<>());
    changes.addLast(event);
    while (changes.size() > RETAINED_RULE_CHANGES) {
      changes.removeFirst();
    }
  }

  record MatchResult(
      CategoricalState state,
      List<String> legalActions,
      Map<String, Double> predictions,
      List<String> unknownActions,
      List<Long> matchingRuleIds,
      Map<String, List<Long>> actionRuleIds) {
    MatchResult {
      legalActions = List.copyOf(legalActions);
      predictions = Map.copyOf(predictions);
      unknownActions = List.copyOf(unknownActions);
      matchingRuleIds = List.copyOf(matchingRuleIds);
      var copied = new LinkedHashMap<String, List<Long>>();
      actionRuleIds.forEach((action, ids) -> copied.put(action, List.copyOf(ids)));
      actionRuleIds = Map.copyOf(copied);
    }
  }

  private record Term(boolean wildcard, String value) {
    static Term wildcardTerm() {
      return new Term(true, "");
    }

    static Term exact(String value) {
      return new Term(false, value);
    }

    boolean matches(String tested) {
      return wildcard || value.equals(tested);
    }
  }

  private record RuleMetrics(
      double prediction,
      double predictionError,
      double fitness,
      long experience,
      double actionSetSize) {
    private static RuleMetrics from(Rule rule) {
      return new RuleMetrics(
          rule.prediction, rule.predictionError, rule.fitness, rule.experience, rule.actionSetSize);
    }
  }

  private static final class Rule {
    private final long id;
    private final List<Term> condition;
    private String actionId;
    private double prediction;
    private double predictionError;
    private double fitness;
    private long experience;
    private int numerosity;
    private double actionSetSize;
    private long timestamp;
    private final List<Long> parentIds;
    private final XcsBirthReason birthReason;

    private Rule(
        long id,
        List<Term> condition,
        String actionId,
        double prediction,
        double predictionError,
        double fitness,
        long experience,
        int numerosity,
        double actionSetSize,
        long timestamp,
        List<Long> parentIds,
        XcsBirthReason birthReason) {
      this.id = id;
      this.condition = condition;
      this.actionId = actionId;
      this.prediction = prediction;
      this.predictionError = predictionError;
      this.fitness = fitness;
      this.experience = experience;
      this.numerosity = numerosity;
      this.actionSetSize = actionSetSize;
      this.timestamp = timestamp;
      this.parentIds = List.copyOf(parentIds);
      this.birthReason = birthReason;
    }

    private Rule copy() {
      return new Rule(
          id,
          new ArrayList<>(condition),
          actionId,
          prediction,
          predictionError,
          fitness,
          experience,
          numerosity,
          actionSetSize,
          timestamp,
          parentIds,
          birthReason);
    }

    private boolean matches(CategoricalState state) {
      if (condition.size() != state.values().size()) {
        return false;
      }
      for (var index = 0; index < condition.size(); index++) {
        if (!condition.get(index).matches(state.values().get(index))) {
          return false;
        }
      }
      return true;
    }

    private boolean sameStructure(Rule other) {
      return actionId.equals(other.actionId) && condition.equals(other.condition);
    }

    private boolean subsumes(Rule child, XcsParameters parameters) {
      if (!actionId.equals(child.actionId)
          || experience <= parameters.subsumptionExperienceThreshold()
          || predictionError >= parameters.errorThreshold()
          || condition.size() != child.condition.size()) {
        return false;
      }
      var strictlyMoreGeneral = false;
      for (var index = 0; index < condition.size(); index++) {
        var own = condition.get(index);
        var other = child.condition.get(index);
        if (own.wildcard && !other.wildcard) {
          strictlyMoreGeneral = true;
        } else if (!own.wildcard && (other.wildcard || !own.value.equals(other.value))) {
          return false;
        }
      }
      return strictlyMoreGeneral;
    }

    private double generality() {
      return condition.stream().filter(Term::wildcard).count() / (double) condition.size();
    }

    private XcsRuleSnapshot snapshot() {
      return new XcsRuleSnapshot(
          id,
          condition.stream().map(term -> term.wildcard ? "*" : term.value).toList(),
          actionId,
          prediction,
          predictionError,
          fitness,
          experience,
          numerosity,
          actionSetSize,
          timestamp,
          parentIds,
          birthReason);
    }
  }
}
