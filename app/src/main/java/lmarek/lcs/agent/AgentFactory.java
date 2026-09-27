package lmarek.lcs.agent;

@FunctionalInterface
public interface AgentFactory<S, M, C> {
  Agent<S, M> create(String id, C configuration, long seed);
}
