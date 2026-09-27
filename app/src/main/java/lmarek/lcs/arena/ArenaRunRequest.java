package lmarek.lcs.arena;

import java.util.Objects;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ArenaRunRequest(
    AgentConfiguration agentA,
    AgentConfiguration agentB,
    @JsonDeserialize(using = CanonicalSeedDeserializer.class) String seed,
    Pacing pacing,
    double evaluationInterval,
    double evaluationGames) {
  public ArenaRunRequest {
    Objects.requireNonNull(agentA);
    Objects.requireNonNull(agentB);
    Objects.requireNonNull(seed);
    Objects.requireNonNull(pacing);
  }

  long parsedSeed() {
    return ArenaConfigurationSchema.parseSeed(seed);
  }

  int evaluationIntervalValue() {
    return Math.toIntExact((long) evaluationInterval);
  }

  int evaluationGamesValue() {
    return Math.toIntExact((long) evaluationGames);
  }
}
