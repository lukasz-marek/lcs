package lmarek.lcs.arena;

import java.util.Locale;
import lmarek.lcs.agent.Agent;
import lmarek.lcs.draughts.DraughtsGame;
import lmarek.lcs.draughts.DraughtsMove;
import lmarek.lcs.draughts.DraughtsState;
import lmarek.lcs.search.MctsAgent;
import lmarek.lcs.search.MctsConfig;
import lmarek.lcs.search.MctsPreset;
import lmarek.lcs.search.RandomAgent;
import lmarek.lcs.xcs.DraughtsXcsEncoder;
import lmarek.lcs.xcs.MatchingExecutor;
import lmarek.lcs.xcs.XcsAgent;
import lmarek.lcs.xcs.XcsParameters;

final class DraughtsAgentFactory {
  private final DraughtsGame game;
  private final MatchingExecutor matchingExecutor;

  DraughtsAgentFactory(DraughtsGame game, MatchingExecutor matchingExecutor) {
    this.matchingExecutor = matchingExecutor;
    this.game = game;
  }

  Agent<DraughtsState, DraughtsMove> create(
      String id, AgentConfiguration configuration, long seed) {
    var displayName = id.equals("A") ? "Challenger A" : "Defender B";
    return switch (configuration.kind()) {
      case RANDOM -> new RandomAgent<>(id, displayName, seed);
      case MCTS -> createMcts(id, displayName, configuration, seed);
      case XCS ->
          new XcsAgent<>(
              id,
              displayName,
              new DraughtsXcsEncoder(),
              xcsParameters(configuration),
              seed,
              matchingExecutor);
    };
  }

  private Agent<DraughtsState, DraughtsMove> createMcts(
      String id, String displayName, AgentConfiguration configuration, long seed) {
    var preset = MctsPreset.valueOf(configuration.preset().toUpperCase(Locale.ROOT));
    int simulations = (int) setting(configuration, "simulations", preset.simulations());
    int rolloutLimit =
        (int) setting(configuration, "rolloutPlyLimit", MctsConfig.DEFAULT_ROLLOUT_PLY_LIMIT);
    var config = new MctsConfig(simulations, rolloutLimit, MctsConfig.UCT_EXPLORATION);
    return new MctsAgent<>(id, displayName, game, config, seed, DraughtsMove::actionId);
  }

  private static XcsParameters xcsParameters(AgentConfiguration configuration) {
    var defaults = XcsParameters.defaults();
    return new XcsParameters(
        (int) setting(configuration, "maximumPopulation", defaults.maximumPopulation()),
        setting(configuration, "beta", defaults.beta()),
        setting(configuration, "gamma", defaults.gamma()),
        setting(configuration, "explorationProbability", defaults.explorationProbability()),
        defaults.accuracyAlpha(),
        defaults.errorThreshold(),
        defaults.accuracyPower(),
        setting(configuration, "wildcardProbability", defaults.wildcardProbability()),
        defaults.initialPrediction(),
        defaults.initialPredictionError(),
        defaults.initialFitness(),
        defaults.gaThreshold(),
        defaults.crossoverProbability(),
        defaults.mutationProbability(),
        defaults.deletionExperienceThreshold(),
        defaults.deletionFitnessFraction(),
        defaults.subsumptionExperienceThreshold(),
        defaults.gaSubsumption());
  }

  private static double setting(
      AgentConfiguration configuration, String name, double defaultValue) {
    return configuration.settings().getOrDefault(name, defaultValue);
  }
}
