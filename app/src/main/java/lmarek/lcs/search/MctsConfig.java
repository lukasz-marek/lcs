package lmarek.lcs.search;

/** Parameters for a fresh-tree UCT search. */
public record MctsConfig(int simulations, int rolloutPlyLimit, double explorationConstant) {
  public static final int DEFAULT_ROLLOUT_PLY_LIMIT = 500;
  public static final double UCT_EXPLORATION = Math.sqrt(2.0);

  public MctsConfig {
    if (simulations <= 0) {
      throw new IllegalArgumentException("simulations must be positive");
    }
    if (rolloutPlyLimit <= 0) {
      throw new IllegalArgumentException("rolloutPlyLimit must be positive");
    }
    if (!Double.isFinite(explorationConstant) || explorationConstant < 0.0) {
      throw new IllegalArgumentException("explorationConstant must be finite and non-negative");
    }
  }

  public static MctsConfig forPreset(MctsPreset preset) {
    return new MctsConfig(preset.simulations(), DEFAULT_ROLLOUT_PLY_LIMIT, UCT_EXPLORATION);
  }

  public static MctsConfig balanced() {
    return forPreset(MctsPreset.BALANCED);
  }
}
