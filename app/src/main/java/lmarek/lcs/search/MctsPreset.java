package lmarek.lcs.search;

/** Simulation budgets exposed by the spectator arena. */
public enum MctsPreset {
  FAST(100),
  BALANCED(500),
  STRONG(2_000);

  private final int simulations;

  MctsPreset(int simulations) {
    this.simulations = simulations;
  }

  public int simulations() {
    return simulations;
  }
}
