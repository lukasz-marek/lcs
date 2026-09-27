package lmarek.lcs.arena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lmarek.lcs.agent.AgentKind;

/** One source of truth for arena options, defaults, and request validation. */
final class ArenaConfigurationSchema {
  static final String DEFAULT_SEED = "20250920";
  static final int DEFAULT_EVALUATION_INTERVAL = 500;
  static final int DEFAULT_EVALUATION_GAMES = 20;

  private static final SettingDefinition MAXIMUM_POPULATION =
      new SettingDefinition(
          "maximumPopulation", "Population cap", 1_000_000, 500, 1_000_000, 1, true, false);
  private static final SettingDefinition BETA =
      new SettingDefinition("beta", "Learning rate β", 0.2, 0.01, 1, 0.01, false, false);
  private static final SettingDefinition GAMMA =
      new SettingDefinition("gamma", "Discount γ", 0.99, 0, 1, 0.01, false, false);
  private static final SettingDefinition EXPLORATION =
      new SettingDefinition(
          "explorationProbability", "Exploration ε", 0.2, 0, 1, 0.01, false, false);
  private static final SettingDefinition WILDCARD =
      new SettingDefinition(
          "wildcardProbability", "Wildcard chance", 0.33, 0, 1, 0.01, false, false);
  private static final SettingDefinition SIMULATIONS =
      new SettingDefinition("simulations", "Simulations", 500, 100, 5_000, 1, true, false);
  private static final SettingDefinition ROLLOUT_LIMIT =
      new SettingDefinition("rolloutPlyLimit", "Rollout ply cap", 500, 25, 1_000, 1, true, false);
  private static final SettingDefinition EVALUATION_INTERVAL =
      new SettingDefinition(
          "evaluationInterval",
          "Evaluation interval",
          DEFAULT_EVALUATION_INTERVAL,
          1,
          1_000_000,
          1,
          true,
          false);
  private static final SettingDefinition EVALUATION_GAMES =
      new SettingDefinition(
          "evaluationGames", "Evaluation games", DEFAULT_EVALUATION_GAMES, 2, 100, 2, true, true);

  private static final Map<AgentKind, AgentDefinition> AGENTS = agentDefinitions();
  private static final ArenaOptions OPTIONS = createOptions();

  private ArenaConfigurationSchema() {}

  static ArenaOptions options() {
    return OPTIONS;
  }

  static void validate(ArenaRunRequest request) {
    parseSeed(request.seed());
    validate(request.agentA(), "agentA");
    validate(request.agentB(), "agentB");
    EVALUATION_INTERVAL.validate(request.evaluationInterval(), "evaluationInterval");
    EVALUATION_GAMES.validate(request.evaluationGames(), "evaluationGames");
  }

  static long parseSeed(String seed) {
    if (!seed.matches("-?(0|[1-9][0-9]*)")) {
      throw new IllegalArgumentException("seed must be a canonical decimal Java long");
    }
    try {
      long parsed = Long.parseLong(seed);
      if (!Long.toString(parsed).equals(seed)) {
        throw new IllegalArgumentException("seed must be a canonical decimal Java long");
      }
      return parsed;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("seed must be a canonical decimal Java long", exception);
    }
  }

  private static void validate(AgentConfiguration configuration, String path) {
    var definition = AGENTS.get(configuration.kind());
    if (definition == null) {
      throw new IllegalArgumentException(path + ".kind is unsupported");
    }
    if (!definition.presets().contains(configuration.preset())) {
      throw new IllegalArgumentException(
          "%s.preset must be one of %s".formatted(path, definition.presets()));
    }
    for (var entry : configuration.settings().entrySet()) {
      var setting = definition.settings().get(entry.getKey());
      if (setting == null) {
        throw new IllegalArgumentException(
            "%s.settings contains unsupported key '%s'".formatted(path, entry.getKey()));
      }
      setting.validate(entry.getValue(), path + ".settings." + entry.getKey());
    }
  }

  private static Map<AgentKind, AgentDefinition> agentDefinitions() {
    return Map.of(
        AgentKind.XCS,
        new AgentDefinition(
            Set.of("STANDARD"), settings(MAXIMUM_POPULATION, BETA, GAMMA, EXPLORATION, WILDCARD)),
        AgentKind.MCTS,
        new AgentDefinition(
            Set.of("FAST", "BALANCED", "STRONG"), settings(SIMULATIONS, ROLLOUT_LIMIT)),
        AgentKind.RANDOM,
        new AgentDefinition(Set.of("UNIFORM"), Map.of()));
  }

  private static Map<String, SettingDefinition> settings(SettingDefinition... definitions) {
    var result = new LinkedHashMap<String, SettingDefinition>();
    for (var definition : definitions) {
      result.put(definition.id(), definition);
    }
    return Map.copyOf(result);
  }

  private static ArenaOptions createOptions() {
    return new ArenaOptions(
        List.of(
            new AgentKindOption(
                "XCS",
                "XCS learner",
                "STANDARD",
                List.of(new PresetOption("STANDARD", "Standard XCS", Map.of())),
                options(MAXIMUM_POPULATION, BETA, GAMMA, EXPLORATION, WILDCARD)),
            new AgentKindOption(
                "MCTS",
                "UCT search",
                "BALANCED",
                List.of(
                    new PresetOption("FAST", "Fast · 100", Map.of("simulations", 100.0)),
                    new PresetOption("BALANCED", "Balanced · 500", Map.of("simulations", 500.0)),
                    new PresetOption("STRONG", "Strong · 2,000", Map.of("simulations", 2_000.0))),
                options(SIMULATIONS, ROLLOUT_LIMIT)),
            new AgentKindOption(
                "RANDOM",
                "Random baseline",
                "UNIFORM",
                List.of(new PresetOption("UNIFORM", "Uniform legal move", Map.of())),
                List.of())),
        List.of("LIVE", "TURBO"),
        DEFAULT_SEED,
        EVALUATION_INTERVAL.option(),
        EVALUATION_GAMES.option());
  }

  private static List<SettingOption> options(SettingDefinition... definitions) {
    return java.util.Arrays.stream(definitions).map(SettingDefinition::option).toList();
  }

  private record AgentDefinition(Set<String> presets, Map<String, SettingDefinition> settings) {}

  private record SettingDefinition(
      String id,
      String label,
      double defaultValue,
      double minimum,
      double maximum,
      double step,
      boolean integral,
      boolean even) {
    SettingOption option() {
      return new SettingOption(id, label, defaultValue, minimum, maximum, step, integral, even);
    }

    void validate(double value, String path) {
      if (!Double.isFinite(value) || value < minimum || value > maximum) {
        throw new IllegalArgumentException(
            "%s must be finite and between %s and %s".formatted(path, minimum, maximum));
      }
      if (integral && value != Math.rint(value)) {
        throw new IllegalArgumentException(path + " must be an integer");
      }
      if (even && ((long) value & 1L) != 0L) {
        throw new IllegalArgumentException(path + " must be even");
      }
    }
  }
}
