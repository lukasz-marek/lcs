package lmarek.lcs.search;

import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicLong;
import lmarek.lcs.agent.Agent;
import lmarek.lcs.agent.AgentDecision;
import lmarek.lcs.agent.AgentKind;
import lmarek.lcs.agent.AgentTelemetry;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.RandomDecisionTrace;

/** A uniformly random legal policy, useful as a reproducible baseline. */
public final class RandomAgent<S, M> implements Agent<S, M> {
  private final String id;
  private final String displayName;
  private final SplittableRandom random;
  private final AtomicLong decisions = new AtomicLong();

  public RandomAgent(String id, String displayName, long seed) {
    this.id = Objects.requireNonNull(id);
    this.displayName = Objects.requireNonNull(displayName);
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
    return AgentKind.RANDOM;
  }

  @Override
  public boolean learns() {
    return false;
  }

  @Override
  public AgentDecision<M> decide(DecisionContext<S, M> context) {
    var legalMoves = context.legalMoves();
    var move = legalMoves.get(random.nextInt(legalMoves.size()));
    decisions.incrementAndGet();
    return new AgentDecision<>(move, new RandomDecisionTrace(legalMoves.size()));
  }

  @Override
  public Agent<S, M> frozenCopy(long seed) {
    return new RandomAgent<>(id, displayName, seed);
  }

  @Override
  public AgentTelemetry telemetry() {
    return new AgentTelemetry(java.util.Map.of("decisions", (double) decisions.get()));
  }
}
