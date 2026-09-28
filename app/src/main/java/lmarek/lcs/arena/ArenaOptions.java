package lmarek.lcs.arena;

import java.util.List;

public record ArenaOptions(
    List<AgentKindOption> agentKinds,
    List<String> pacingModes,
    String defaultSeed,
    SettingOption evaluationInterval,
    SettingOption evaluationGames,
    ArenaPerformance performance,
    List<String> trainingModes,
    int availableTrainingWorkers) {
  public ArenaOptions(
      List<AgentKindOption> agentKinds,
      List<String> pacingModes,
      String defaultSeed,
      SettingOption evaluationInterval,
      SettingOption evaluationGames) {
    this(
        agentKinds,
        pacingModes,
        defaultSeed,
        evaluationInterval,
        evaluationGames,
        ArenaPerformance.defaults());
  }

  public ArenaOptions(
      List<AgentKindOption> agentKinds,
      List<String> pacingModes,
      String defaultSeed,
      SettingOption evaluationInterval,
      SettingOption evaluationGames,
      ArenaPerformance performance) {
    this(
        agentKinds,
        pacingModes,
        defaultSeed,
        evaluationInterval,
        evaluationGames,
        performance,
        List.of("STANDARD", "FULL_SPEED"),
        Runtime.getRuntime().availableProcessors());
  }

  public ArenaOptions {
    agentKinds = List.copyOf(agentKinds);
    pacingModes = List.copyOf(pacingModes);
    trainingModes = List.copyOf(trainingModes);
  }
}
