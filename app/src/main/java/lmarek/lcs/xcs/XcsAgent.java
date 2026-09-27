package lmarek.lcs.xcs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import lmarek.lcs.agent.Agent;
import lmarek.lcs.agent.AgentDecision;
import lmarek.lcs.agent.AgentKind;
import lmarek.lcs.agent.AgentTelemetry;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.EpisodeContext;
import lmarek.lcs.agent.EpisodeResult;
import lmarek.lcs.agent.MoveTransition;
import lmarek.lcs.agent.XcsDecisionTrace;
import lmarek.lcs.game.Player;
import org.jspecify.annotations.Nullable;

/** Conventional categorical XCS adapted to alternating-turn games. */
public final class XcsAgent<S, M> implements Agent<S, M>, XcsInspectable {
  private final String id;
  private final String displayName;
  private final StateActionEncoder<S, M> encoder;
  private final XcsParameters parameters;
  private final RandomGenerator random;
  private final XcsPopulation population;
  private final boolean frozen;
  private Optional<Player> seat = Optional.empty();
  private Optional<PendingDecision> pending = Optional.empty();
  private boolean trainingEpisode;

  public XcsAgent(
      String id,
      String displayName,
      StateActionEncoder<S, M> encoder,
      XcsParameters parameters,
      long seed) {
    this(id, displayName, encoder, parameters, new SplittableRandom(seed), null, false);
  }

  public XcsAgent(
      String id,
      String displayName,
      StateActionEncoder<S, M> encoder,
      XcsParameters parameters,
      long seed,
      MatchingExecutor executor) {
    this(id, displayName, encoder, parameters, seed);
    population.matchingExecutor(executor);
  }

  private XcsAgent(
      String id,
      String displayName,
      StateActionEncoder<S, M> encoder,
      XcsParameters parameters,
      RandomGenerator random,
      @Nullable XcsPopulation sourcePopulation,
      boolean frozen) {
    this.id = Objects.requireNonNull(id);
    this.displayName = Objects.requireNonNull(displayName);
    this.encoder = Objects.requireNonNull(encoder);
    this.parameters = Objects.requireNonNull(parameters);
    this.random = Objects.requireNonNull(random);
    this.population =
        sourcePopulation == null
            ? new XcsPopulation(parameters, random)
            : sourcePopulation.deepCopy(random);
    this.frozen = frozen;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String displayName() {
    return displayName;
  }

  @Override
  public AgentKind kind() {
    return AgentKind.XCS;
  }

  @Override
  public boolean learns() {
    return !frozen;
  }

  @Override
  public synchronized void beginEpisode(EpisodeContext<S> context) {
    seat = Optional.of(context.seat());
    trainingEpisode = context.training() && !frozen;
    pending = Optional.empty();
  }

  @Override
  public synchronized AgentDecision<M> decide(DecisionContext<S, M> context) {
    var perspective =
        seat.orElseThrow(() -> new IllegalStateException("beginEpisode must precede decide"));
    if (context.player() != perspective) {
      throw new IllegalArgumentException(
          "XCS agent seated as %s was asked to move as %s"
              .formatted(perspective, context.player()));
    }
    var learning = trainingEpisode && context.training() && !frozen;
    if (learning) {
      population.advanceIteration();
    }
    var state = encoder.encode(context.state(), perspective);
    var legalMoves = legalMoveMap(context.legalMoves(), perspective);
    var protectedBeforeMatch =
        pending.map(value -> Set.copyOf(value.actionSetRuleIds())).orElseGet(Set::of);
    var match =
        population.match(
            state, new ArrayList<>(legalMoves.keySet()), learning, protectedBeforeMatch);

    if (learning && pending.isPresent()) {
      var previous = pending.orElseThrow();
      var target = parameters.gamma() * maximumPrediction(match.predictions());
      population.update(previous.actionSetRuleIds(), target);
      var protectedForGa = new HashSet<>(match.matchingRuleIds());
      protectedForGa.addAll(previous.actionSetRuleIds());
      population.runGeneticAlgorithm(
          previous.actionSetRuleIds(), previous.state(), previous.legalActions(), protectedForGa);
    }

    var exploratory = learning && random.nextDouble() < parameters.explorationProbability();
    var selectedAction =
        exploratory
            ? match.legalActions().get(random.nextInt(match.legalActions().size()))
            : bestAction(match.predictions(), match.legalActions());
    var selectedRuleIds = match.actionRuleIds().getOrDefault(selectedAction, List.of());
    if (learning) {
      pending =
          Optional.of(
              new PendingDecision(state, match.legalActions(), List.copyOf(selectedRuleIds)));
    } else {
      pending = Optional.empty();
    }

    var trace =
        new XcsDecisionTrace(
            match.predictions(),
            selectedAction,
            exploratory,
            match.unknownActions(),
            match.matchingRuleIds(),
            selectedRuleIds);
    return new AgentDecision<>(
        Objects.requireNonNull(legalMoves.get(selectedAction), "Selected action has no legal move"),
        trace);
  }

  @Override
  public void observe(MoveTransition<S, M> transition) {
    // XCS updates when this player next acts, so an opponent transition remains deliberately
    // pending.
  }

  @Override
  public synchronized void endEpisode(EpisodeResult<S, M> result) {
    if (trainingEpisode && result.training() && !frozen && pending.isPresent()) {
      var finalDecision = pending.orElseThrow();
      population.update(finalDecision.actionSetRuleIds(), result.reward());
      population.runGeneticAlgorithm(
          finalDecision.actionSetRuleIds(),
          finalDecision.state(),
          finalDecision.legalActions(),
          Set.copyOf(finalDecision.actionSetRuleIds()));
    }
    pending = Optional.empty();
    trainingEpisode = false;
  }

  @Override
  public synchronized void abortEpisode() {
    pending = Optional.empty();
    seat = Optional.empty();
    trainingEpisode = false;
  }

  @Override
  public synchronized XcsAgent<S, M> frozenCopy(long seed) {
    return new XcsAgent<>(
        id, displayName, encoder, parameters, new SplittableRandom(seed), population, true);
  }

  @Override
  public synchronized AgentTelemetry telemetry() {
    var metrics = new LinkedHashMap<>(population.telemetry());
    metrics.put("xcs.frozen", frozen ? 1.0 : 0.0);
    return new AgentTelemetry(metrics);
  }

  @Override
  public synchronized List<XcsRuleSnapshot> rules() {
    return population.snapshots();
  }

  @Override
  public synchronized Optional<XcsRuleSnapshot> rule(long ruleId) {
    return population.snapshot(ruleId);
  }

  @Override
  public synchronized Optional<XcsRuleInspection> inspectRule(long ruleId) {
    return population
        .snapshot(ruleId)
        .map(rule -> new XcsRuleInspection(rule, population.ruleChanges(ruleId)));
  }

  @Override
  public synchronized List<XcsEvolutionEvent> evolutionEventsAfter(long sequence, int limit) {
    return population.eventsAfter(sequence, limit);
  }

  @Override
  public synchronized List<XcsEvolutionEvent> ruleChanges(long ruleId) {
    return population.ruleChanges(ruleId);
  }

  private Map<String, M> legalMoveMap(List<M> legalMoves, Player perspective) {
    var byAction = new LinkedHashMap<String, M>();
    for (var move : legalMoves) {
      var action = encoder.actionId(move, perspective);
      var previous = byAction.put(action, move);
      if (previous != null) {
        throw new IllegalArgumentException(
            "Encoder produced duplicate action ID '%s' for distinct legal moves".formatted(action));
      }
    }
    return byAction;
  }

  private String bestAction(Map<String, Double> predictions, List<String> legalActions) {
    var maximum = maximumPrediction(predictions);
    var best =
        legalActions.stream()
            .filter(action -> Double.compare(predictions.getOrDefault(action, 0.0), maximum) == 0)
            .toList();
    return best.get(random.nextInt(best.size()));
  }

  private static double maximumPrediction(Map<String, Double> predictions) {
    return predictions.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
  }

  private record PendingDecision(
      CategoricalState state, List<String> legalActions, List<Long> actionSetRuleIds) {
    private PendingDecision {
      legalActions = List.copyOf(legalActions);
      actionSetRuleIds = List.copyOf(actionSetRuleIds);
    }
  }
}
