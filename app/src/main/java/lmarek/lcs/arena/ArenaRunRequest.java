package lmarek.lcs.arena;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.annotation.JsonDeserialize;

public record ArenaRunRequest(
    AgentConfiguration agentA,
    AgentConfiguration agentB,
    @JsonDeserialize(using = CanonicalSeedDeserializer.class) String seed,
    Pacing pacing,
    double evaluationInterval,
    double evaluationGames,
    TrainingMode trainingMode,
    @Nullable Integer trainingWorkers) {
  public ArenaRunRequest(
      AgentConfiguration agentA,
      AgentConfiguration agentB,
      String seed,
      Pacing pacing,
      double evaluationInterval,
      double evaluationGames) {
    this(
        agentA,
        agentB,
        seed,
        pacing,
        evaluationInterval,
        evaluationGames,
        TrainingMode.STANDARD,
        null);
  }

  public ArenaRunRequest {
    Objects.requireNonNull(agentA);
    Objects.requireNonNull(agentB);
    Objects.requireNonNull(seed);
    Objects.requireNonNull(pacing);
    trainingMode = trainingMode == null ? TrainingMode.STANDARD : trainingMode;
    if (trainingWorkers != null && trainingWorkers < 1) {
      throw new IllegalArgumentException("trainingWorkers must be positive");
    }
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
