package lmarek.lcs.xcs;

/** Parameters for the conventional Michigan-style XCS implementation. */
public record XcsParameters(
    int maximumPopulation,
    double beta,
    double gamma,
    double explorationProbability,
    double accuracyAlpha,
    double errorThreshold,
    double accuracyPower,
    double wildcardProbability,
    double initialPrediction,
    double initialPredictionError,
    double initialFitness,
    int gaThreshold,
    double crossoverProbability,
    double mutationProbability,
    int deletionExperienceThreshold,
    double deletionFitnessFraction,
    int subsumptionExperienceThreshold,
    boolean gaSubsumption) {

  public XcsParameters {
    positive(maximumPopulation, "maximumPopulation");
    probability(beta, "beta");
    unitInterval(gamma, "gamma");
    unitInterval(explorationProbability, "explorationProbability");
    positiveFinite(accuracyAlpha, "accuracyAlpha");
    positiveFinite(errorThreshold, "errorThreshold");
    positiveFinite(accuracyPower, "accuracyPower");
    unitInterval(wildcardProbability, "wildcardProbability");
    finite(initialPrediction, "initialPrediction");
    nonNegativeFinite(initialPredictionError, "initialPredictionError");
    positiveFinite(initialFitness, "initialFitness");
    nonNegative(gaThreshold, "gaThreshold");
    unitInterval(crossoverProbability, "crossoverProbability");
    unitInterval(mutationProbability, "mutationProbability");
    nonNegative(deletionExperienceThreshold, "deletionExperienceThreshold");
    positiveFinite(deletionFitnessFraction, "deletionFitnessFraction");
    nonNegative(subsumptionExperienceThreshold, "subsumptionExperienceThreshold");
  }

  public static XcsParameters defaults() {
    return new XcsParameters(
        1_000_000, 0.2, 0.99, 0.2, 0.1, 0.01, 5.0, 0.33, 0.0, 0.0, 0.01, 25, 0.8, 0.04, 20, 0.1, 20,
        true);
  }

  private static void probability(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be in (0, 1]");
    }
  }

  private static void unitInterval(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
      throw new IllegalArgumentException(name + " must be in [0, 1]");
    }
  }

  private static void positiveFinite(double value, String name) {
    if (!Double.isFinite(value) || value <= 0.0) {
      throw new IllegalArgumentException(name + " must be positive and finite");
    }
  }

  private static void nonNegativeFinite(double value, String name) {
    if (!Double.isFinite(value) || value < 0.0) {
      throw new IllegalArgumentException(name + " must be non-negative and finite");
    }
  }

  private static void finite(double value, String name) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(name + " must be finite");
    }
  }

  private static void positive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }

  private static void nonNegative(int value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must be non-negative");
    }
  }
}
