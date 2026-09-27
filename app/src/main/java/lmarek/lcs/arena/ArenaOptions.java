package lmarek.lcs.arena;

import java.util.List;

public record ArenaOptions(
    List<AgentKindOption> agentKinds,
    List<String> pacingModes,
    String defaultSeed,
    SettingOption evaluationInterval,
    SettingOption evaluationGames,
    ArenaPerformance performance) {
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

  public ArenaOptions {
    agentKinds = List.copyOf(agentKinds);
    pacingModes = List.copyOf(pacingModes);
  }
}
