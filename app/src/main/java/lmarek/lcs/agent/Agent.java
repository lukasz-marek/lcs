package lmarek.lcs.agent;

/** A game-playing policy with optional episode-to-episode learning. */
public interface Agent<S, M> {
  String id();

  String displayName();

  AgentKind kind();

  boolean learns();

  default void beginEpisode(EpisodeContext<S> context) {}

  AgentDecision<M> decide(DecisionContext<S, M> context);

  default void observe(MoveTransition<S, M> transition) {}

  default void endEpisode(EpisodeResult<S, M> result) {}

  /** Clears any episode-local state after an episode exits abnormally. */
  default void abortEpisode() {}

  Agent<S, M> frozenCopy(long seed);

  default AgentTelemetry telemetry() {
    return AgentTelemetry.empty();
  }
}
