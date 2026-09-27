package lmarek.lcs.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import lmarek.lcs.agent.Agent;
import lmarek.lcs.agent.AgentDecision;
import lmarek.lcs.agent.AgentKind;
import lmarek.lcs.agent.AgentTelemetry;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.MctsDecisionTrace;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.PositionAnalysis;
import lmarek.lcs.game.TurnBasedGame;
import org.jspecify.annotations.Nullable;

/** Fresh-tree Monte Carlo tree search using the UCT selection rule. */
public final class MctsAgent<S, M> implements Agent<S, M> {
  private final String id;
  private final String displayName;
  private final TurnBasedGame<S, M> game;
  private final MctsConfig config;
  private final Function<M, String> moveLabel;
  private final SplittableRandom random;
  private final AtomicLong decisions = new AtomicLong();
  private final AtomicLong totalTruncatedRollouts = new AtomicLong();
  private volatile long lastTruncatedRollouts;

  public MctsAgent(String id, String displayName, TurnBasedGame<S, M> game, long seed) {
    this(id, displayName, game, MctsConfig.balanced(), seed, Object::toString);
  }

  public MctsAgent(
      String id, String displayName, TurnBasedGame<S, M> game, MctsPreset preset, long seed) {
    this(id, displayName, game, MctsConfig.forPreset(preset), seed, Object::toString);
  }

  public MctsAgent(
      String id, String displayName, TurnBasedGame<S, M> game, MctsConfig config, long seed) {
    this(id, displayName, game, config, seed, Object::toString);
  }

  public MctsAgent(
      String id,
      String displayName,
      TurnBasedGame<S, M> game,
      MctsConfig config,
      long seed,
      Function<M, String> moveLabel) {
    this.id = Objects.requireNonNull(id);
    this.displayName = Objects.requireNonNull(displayName);
    this.game = Objects.requireNonNull(game);
    this.config = Objects.requireNonNull(config);
    this.moveLabel = Objects.requireNonNull(moveLabel);
    random = new SplittableRandom(seed);
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
    return AgentKind.MCTS;
  }

  @Override
  public boolean learns() {
    return false;
  }

  @Override
  public AgentDecision<M> decide(DecisionContext<S, M> context) {
    checkInterrupted();
    var root = Node.root(game.analyze(context.state()), context.legalMoves());
    long truncatedRollouts = 0;

    for (int simulation = 0; simulation < config.simulations(); simulation++) {
      checkInterrupted();
      var node = descend(root);
      var result = node.outcome.isPresent() ? node.outcome : rollout(node.analysis);
      if (result.isEmpty()) {
        truncatedRollouts++;
      }
      backUp(node, result);
    }

    checkInterrupted();
    var selected = mostVisitedChild(root);
    var trace = trace(context.legalMoves(), root, selected, truncatedRollouts);
    decisions.incrementAndGet();
    lastTruncatedRollouts = truncatedRollouts;
    totalTruncatedRollouts.addAndGet(truncatedRollouts);
    return new AgentDecision<>(selected.incomingMove(), trace);
  }

  private Node<S, M> descend(Node<S, M> root) {
    var node = root;
    while (node.outcome.isEmpty()) {
      checkInterrupted();
      if (!node.unexpandedMoves.isEmpty()) {
        return expand(node);
      }
      node = selectByUct(node);
    }
    return node;
  }

  private Node<S, M> expand(Node<S, M> parent) {
    checkInterrupted();
    int selectedIndex = random.nextInt(parent.unexpandedMoves.size());
    var move = parent.unexpandedMoves.remove(selectedIndex);
    var chooser = game.playerToMove(parent.state);
    var nextState = game.applyAnalyzedMove(parent.analysis, move);
    checkInterrupted();
    var child = Node.child(nextState, parent, move, chooser, game);
    parent.children.add(child);
    return child;
  }

  private Node<S, M> selectByUct(Node<S, M> parent) {
    double logarithm = Math.log((double) parent.visits);
    double bestScore = Double.NEGATIVE_INFINITY;
    var best = new ArrayList<Node<S, M>>();
    for (var child : parent.children) {
      checkInterrupted();
      double exploitation = child.valueSum / child.visits;
      double exploration =
          config.explorationConstant() * Math.sqrt(logarithm / (double) child.visits);
      double score = exploitation + exploration;
      int comparison = Double.compare(score, bestScore);
      if (comparison > 0) {
        bestScore = score;
        best.clear();
        best.add(child);
      } else if (comparison == 0) {
        best.add(child);
      }
    }
    if (best.isEmpty()) {
      throw new IllegalStateException("A non-terminal, fully expanded node has no children");
    }
    return best.get(random.nextInt(best.size()));
  }

  private Optional<GameOutcome> rollout(PositionAnalysis<S, M> startingAnalysis) {
    var analysis = startingAnalysis;
    for (int ply = 0; ply < config.rolloutPlyLimit(); ply++) {
      checkInterrupted();
      var outcome = analysis.outcome();
      if (outcome.isPresent()) {
        return outcome;
      }
      var legalMoves = analysis.legalMoves();
      if (legalMoves.isEmpty()) {
        throw new IllegalStateException("Game returned no legal moves for a non-terminal state");
      }
      var move = legalMoves.get(random.nextInt(legalMoves.size()));
      analysis = game.analyze(game.applyAnalyzedMove(analysis, move));
    }
    checkInterrupted();
    return analysis.outcome();
  }

  private static <S, M> void backUp(Node<S, M> leaf, Optional<GameOutcome> outcome) {
    @Nullable Node<S, M> node = leaf;
    while (node != null) {
      checkInterrupted();
      node.visits++;
      if (node.chooser != null && outcome.isPresent()) {
        node.valueSum += outcome.orElseThrow().rewardFor(node.chooser);
      }
      node = node.parent;
    }
  }

  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted()) {
      throw new CancellationException("MCTS decision interrupted");
    }
  }

  private Node<S, M> mostVisitedChild(Node<S, M> root) {
    long mostVisits = -1;
    var best = new ArrayList<Node<S, M>>();
    for (var child : root.children) {
      int comparison = Long.compare(child.visits, mostVisits);
      if (comparison > 0) {
        mostVisits = child.visits;
        best.clear();
        best.add(child);
      } else if (comparison == 0) {
        best.add(child);
      }
    }
    if (best.isEmpty()) {
      throw new IllegalStateException("MCTS completed without visiting a root move");
    }
    return best.get(random.nextInt(best.size()));
  }

  private MctsDecisionTrace trace(
      List<M> legalMoves, Node<S, M> root, Node<S, M> selected, long truncatedRollouts) {
    Map<String, Long> visits = new LinkedHashMap<>();
    Map<String, Double> meanValues = new LinkedHashMap<>();
    @Nullable String selectedMove = null;
    for (var move : legalMoves) {
      String label = Objects.requireNonNull(moveLabel.apply(move));
      if (visits.containsKey(label)) {
        throw new IllegalStateException("Move labels must be unique: " + label);
      }
      var child = childForMove(root, move);
      if (child == selected) {
        selectedMove = label;
      }
      long moveVisits = child == null ? 0 : child.visits;
      double meanValue = child == null || child.visits == 0 ? 0.0 : child.valueSum / child.visits;
      visits.put(label, moveVisits);
      meanValues.put(label, meanValue);
    }
    return new MctsDecisionTrace(
        Objects.requireNonNull(selectedMove),
        visits,
        meanValues,
        config.simulations(),
        truncatedRollouts);
  }

  private static <S, M> @Nullable Node<S, M> childForMove(Node<S, M> root, M move) {
    for (var child : root.children) {
      if (Objects.equals(child.incomingMove, move)) {
        return child;
      }
    }
    return null;
  }

  @Override
  public Agent<S, M> frozenCopy(long seed) {
    return new MctsAgent<>(id, displayName, game, config, seed, moveLabel);
  }

  @Override
  public AgentTelemetry telemetry() {
    return new AgentTelemetry(
        Map.of(
            "decisions", (double) decisions.get(),
            "lastTruncatedRollouts", (double) lastTruncatedRollouts,
            "totalTruncatedRollouts", (double) totalTruncatedRollouts.get()));
  }

  private static final class Node<S, M> {
    private final S state;
    private final PositionAnalysis<S, M> analysis;
    private final @Nullable Node<S, M> parent;
    private final @Nullable M incomingMove;
    private final @Nullable Player chooser;
    private final Optional<GameOutcome> outcome;
    private final List<M> unexpandedMoves;
    private final List<Node<S, M>> children = new ArrayList<>();
    private long visits;
    private double valueSum;

    private Node(
        PositionAnalysis<S, M> analysis,
        @Nullable Node<S, M> parent,
        @Nullable M incomingMove,
        @Nullable Player chooser,
        Optional<GameOutcome> outcome,
        List<M> unexpandedMoves) {
      this.state = analysis.state();
      this.analysis = analysis;
      this.parent = parent;
      this.incomingMove = incomingMove;
      this.chooser = chooser;
      this.outcome = outcome;
      this.unexpandedMoves = new ArrayList<>(unexpandedMoves);
      if (outcome.isEmpty() && unexpandedMoves.isEmpty()) {
        throw new IllegalStateException("Game returned no legal moves for a non-terminal state");
      }
    }

    private static <S, M> Node<S, M> root(PositionAnalysis<S, M> analysis, List<M> legalMoves) {
      return new Node<>(analysis, null, null, null, Optional.empty(), legalMoves);
    }

    private static <S, M> Node<S, M> child(
        S state, Node<S, M> parent, M move, Player chooser, TurnBasedGame<S, M> game) {
      var analysis = game.analyze(state);
      var outcome = analysis.outcome();
      var legalMoves = outcome.isPresent() ? List.<M>of() : analysis.legalMoves();
      return new Node<>(analysis, parent, move, chooser, outcome, legalMoves);
    }

    private M incomingMove() {
      return Objects.requireNonNull(incomingMove);
    }
  }
}
