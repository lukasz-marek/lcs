package lmarek.lcs.xcs;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Mutable XCS population. All randomness enters through the constructor. */
@SuppressWarnings("ReferenceEquality") // The wildcard sentinel is compared by identity.
final class XcsPopulation {
  private static final int RETAINED_EVENTS = 2_000;
  private static final int RETAINED_RULE_CHANGES = 50;

  private final XcsParameters parameters;
  private final RandomGenerator random;
  private final Map<Long, Rule> byId = new LinkedHashMap<>();
  private final Collection<Rule> rules = byId.values();
  private final Map<String, Map<Long, Rule>> byAction = new HashMap<>();
  private final Map<Structure, Map<Long, Rule>> byStructure = new HashMap<>();
  private int microCount;
  private double generalitySum;
  private double errorSum;
  private double fitnessSum;
  private MatchingExecutor matchingExecutor = MatchingExecutor.sequential();
  private final Deque<XcsEvolutionEvent> events;
  private final Map<Long, Deque<RuleChange>> changesByRule;
  private long nextRuleId;
  private long nextEventSequence;
  private long iteration;
  private long coveringCount;
  private long gaCount;

  XcsPopulation(XcsParameters parameters, RandomGenerator random) {
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

  private XcsPopulation(
      XcsParameters parameters,
      RandomGenerator random,
      List<Rule> rules,
      Deque<XcsEvolutionEvent> events,
      Map<Long, Deque<RuleChange>> changesByRule,
      long nextRuleId,
      long nextEventSequence,
      long iteration,
      long coveringCount,
      long gaCount) {
    this.parameters = parameters;
    this.random = random;
    rules.forEach(this::addRule);
    this.events = events;
    this.changesByRule = changesByRule;
    this.nextRuleId = nextRuleId;
    this.nextEventSequence = nextEventSequence;
    this.iteration = iteration;
    this.coveringCount = coveringCount;
    this.gaCount = gaCount;
  }

  XcsPopulation deepCopy(RandomGenerator copyRandom) {
    var copiedRules = new ArrayList<Rule>(rules.size());
    rules.forEach(rule -> copiedRules.add(rule.copy()));
    var copiedChanges = new LinkedHashMap<Long, Deque<RuleChange>>();
    changesByRule.forEach(
        (ruleId, changes) -> copiedChanges.put(ruleId, new ArrayDeque<>(changes)));
    var copy =
        new XcsPopulation(
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
    copy.matchingExecutor = matchingExecutor;
    return copy;
  }

  void matchingExecutor(MatchingExecutor executor) {
    matchingExecutor = executor;
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
        addRule(rule);
        matching.add(rule);
        coveringCount++;
        recordEvent(
            XcsEvolutionEvent.Type.COVERED,
            rule.id,
            rule.parentIds,
            "Covered an action that had no matching classifier: " + action);
      }
    }

    var rulesByAction = new HashMap<String, List<Rule>>();
    for (var rule : matching) {
      rulesByAction.computeIfAbsent(rule.actionId, ignored -> new ArrayList<>()).add(rule);
    }
    var predictions = new LinkedHashMap<String, Double>();
    var actionRuleIds = new LinkedHashMap<String, List<Long>>();
    for (var action : legal) {
      var actionRules = rulesByAction.getOrDefault(action, List.of());
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
        errorSum += rule.predictionError - old.predictionError;
        fitnessSum += rule.fitness - old.fitness;
        retainRuleChange(rule.id, MetricChange.from(nextEventSequence++, iteration, rule, old));
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
    return rules.stream().map(Rule::snapshot).toList();
  }

  Optional<XcsRuleSnapshot> snapshot(long ruleId) {
    return Optional.ofNullable(byId.get(ruleId)).map(Rule::snapshot);
  }

  List<XcsEvolutionEvent> eventsAfter(long sequence, int limit) {
    if (limit < 1) {
      return List.of();
    }
    return events.stream().filter(event -> event.sequence() > sequence).limit(limit).toList();
  }

  List<XcsEvolutionEvent> ruleChanges(long ruleId) {
    var changes = changesByRule.get(ruleId);
    return changes == null ? List.of() : changes.stream().map(RuleChange::event).toList();
  }

  Map<String, Double> telemetry() {
    var macroCount = rules.size();
    var microCount = microPopulationSize();
    var averageGenerality = macroCount == 0 ? 0.0 : generalitySum / macroCount;
    var averageError = macroCount == 0 ? 0.0 : errorSum / macroCount;
    var averageFitness = macroCount == 0 ? 0.0 : fitnessSum / macroCount;
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
    var cursors =
        new PriorityQueue<RuleCursor>(Comparator.comparingLong(cursor -> cursor.rule().id));
    for (var action : legalActions) {
      var bucket = byAction.get(action);
      if (bucket != null && !bucket.isEmpty()) {
        var iterator = bucket.values().iterator();
        cursors.add(new RuleCursor(iterator, iterator.next()));
      }
    }
    var candidates = new ArrayList<Rule>();
    while (!cursors.isEmpty()) {
      var cursor = cursors.remove();
      candidates.add(cursor.rule());
      if (cursor.iterator().hasNext()) {
        cursors.add(new RuleCursor(cursor.iterator(), cursor.iterator().next()));
      }
    }
    return matchingExecutor.filter(candidates, rule -> rule.matches(state));
  }

  private Rule coveredRule(CategoricalState state, String action) {
    var condition = new Object[state.values().size()];
    for (int i = 0; i < condition.length; i++) {
      var value = state.values().get(i);
      condition[i] = random.nextDouble() < parameters.wildcardProbability() ? WILDCARD : value;
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
            parent.condition.clone(),
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
    var size = first.condition.length;
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
      var temporary = first.condition[index];
      first.condition[index] = second.condition[index];
      second.condition[index] = temporary;
    }
  }

  private void mutate(Rule child, CategoricalState state, List<String> legalActions) {
    var changes = new ArrayList<String>();
    for (var index = 0; index < child.condition.length; index++) {
      if (random.nextDouble() < parameters.mutationProbability()) {
        var current = child.condition[index];
        child.condition[index] = current == WILDCARD ? state.values().get(index) : WILDCARD;
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
          microCount++;
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
    var bucket = byStructure.get(structure(child));
    var identical = bucket == null ? Optional.<Rule>empty() : bucket.values().stream().findFirst();
    if (identical.isPresent()) {
      var resident = identical.orElseThrow();
      if (makeRoomForOne(protectRecipient(protectedRuleIds, resident.id))) {
        resident.numerosity++;
        microCount++;
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
      addRule(child);
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
    microCount--;
    if (selected.numerosity == 0) {
      removeRule(selected);
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
    return ids.stream().distinct().sorted().map(byId::get).filter(Objects::nonNull).toList();
  }

  private int microPopulationSize() {
    return microCount;
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
      retainRuleChange(ruleId, new EventChange(event));
    }
  }

  private void retainRuleChange(long ruleId, RuleChange event) {
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

  private record RuleCursor(Iterator<Rule> iterator, Rule rule) {}

  private static final Object WILDCARD = new Object();

  private record Structure(String action, List<Object> condition) {}

  private static Structure structure(Rule rule) {
    return new Structure(rule.actionId, Arrays.asList(rule.condition));
  }

  private void addRule(Rule rule) {
    byId.put(rule.id, rule);
    byAction.computeIfAbsent(rule.actionId, ignored -> new LinkedHashMap<>()).put(rule.id, rule);
    byStructure
        .computeIfAbsent(structure(rule), ignored -> new LinkedHashMap<>())
        .put(rule.id, rule);
    microCount += rule.numerosity;
    generalitySum += rule.generality();
    errorSum += rule.predictionError;
    fitnessSum += rule.fitness;
  }

  private void removeRule(Rule rule) {
    byId.remove(rule.id);
    var actions = Objects.requireNonNull(byAction.get(rule.actionId));
    actions.remove(rule.id);
    if (actions.isEmpty()) byAction.remove(rule.actionId);
    var key = structure(rule);
    var structures = Objects.requireNonNull(byStructure.get(key));
    structures.remove(rule.id);
    if (structures.isEmpty()) byStructure.remove(key);
    generalitySum -= rule.generality();
    errorSum -= rule.predictionError;
    fitnessSum -= rule.fitness;
  }

  private interface RuleChange {
    XcsEvolutionEvent event();
  }

  private record EventChange(XcsEvolutionEvent event) implements RuleChange {}

  private record MetricChange(
      long sequence,
      long iteration,
      long ruleId,
      List<Long> parents,
      double oldPrediction,
      double prediction,
      double oldError,
      double error,
      double oldFitness,
      double fitness,
      long oldExperience,
      long experience,
      int numerosity,
      double oldActionSetSize,
      double actionSetSize)
      implements RuleChange {
    static MetricChange from(long sequence, long iteration, Rule rule, RuleMetrics before) {
      return new MetricChange(
          sequence,
          iteration,
          rule.id,
          rule.parentIds,
          before.prediction,
          rule.prediction,
          before.predictionError,
          rule.predictionError,
          before.fitness,
          rule.fitness,
          before.experience,
          rule.experience,
          rule.numerosity,
          before.actionSetSize,
          rule.actionSetSize);
    }

    @Override
    public XcsEvolutionEvent event() {
      return new XcsEvolutionEvent(
          sequence,
          iteration,
          XcsEvolutionEvent.Type.UPDATED,
          ruleId,
          parents,
          "prediction %s -> %s; error %s -> %s; fitness %s -> %s; experience %d -> %d; numerosity %d; action-set size %s -> %s"
              .formatted(
                  oldPrediction,
                  prediction,
                  oldError,
                  error,
                  oldFitness,
                  fitness,
                  oldExperience,
                  experience,
                  numerosity,
                  oldActionSetSize,
                  actionSetSize));
    }
  }

  // Expensive reconstruction used by regression tests, never on the decision path.
  void verifyIndexes() {
    var actions = new HashMap<String, Map<Long, Rule>>();
    var structures = new HashMap<Structure, Map<Long, Rule>>();
    long last = 0;
    int count = 0;
    for (var rule : rules) {
      if (rule.id <= last) throw new IllegalStateException("Resident IDs out of order");
      last = rule.id;
      count += rule.numerosity;
      actions.computeIfAbsent(rule.actionId, ignored -> new LinkedHashMap<>()).put(rule.id, rule);
      structures
          .computeIfAbsent(structure(rule), ignored -> new LinkedHashMap<>())
          .put(rule.id, rule);
    }
    if (count != microCount || !actions.equals(byAction) || !structures.equals(byStructure)) {
      throw new IllegalStateException("Population indexes differ from reconstruction");
    }
    if (!byId.keySet().containsAll(changesByRule.keySet())) {
      throw new IllegalStateException("Non-resident history retained");
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
    private final Object[] condition;
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
        Object[] condition,
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
          condition.clone(),
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
      if (condition.length != state.values().size()) {
        return false;
      }
      for (var index = 0; index < condition.length; index++) {
        if (condition[index] != WILDCARD && !condition[index].equals(state.values().get(index))) {
          return false;
        }
      }
      return true;
    }

    private boolean subsumes(Rule child, XcsParameters parameters) {
      if (!actionId.equals(child.actionId)
          || experience <= parameters.subsumptionExperienceThreshold()
          || predictionError >= parameters.errorThreshold()
          || condition.length != child.condition.length) {
        return false;
      }
      var strictlyMoreGeneral = false;
      for (var index = 0; index < condition.length; index++) {
        var own = condition[index];
        var other = child.condition[index];
        if ((own == WILDCARD) && !(other == WILDCARD)) {
          strictlyMoreGeneral = true;
        } else if (!(own == WILDCARD) && ((other == WILDCARD) || !own.equals(other))) {
          return false;
        }
      }
      return strictlyMoreGeneral;
    }

    private double generality() {
      return Arrays.stream(condition).filter(term -> term == WILDCARD).count()
          / (double) condition.length;
    }

    private XcsRuleSnapshot snapshot() {
      return new XcsRuleSnapshot(
          id,
          Arrays.stream(condition).map(term -> term == WILDCARD ? "*" : (String) term).toList(),
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
