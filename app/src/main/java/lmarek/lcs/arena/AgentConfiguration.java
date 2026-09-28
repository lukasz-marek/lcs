package lmarek.lcs.arena;

import java.util.Map;
import java.util.Objects;
import lmarek.lcs.agent.AgentKind;
import org.jspecify.annotations.Nullable;

public record AgentConfiguration(
    AgentKind kind, String preset, Map<String, Double> settings, @Nullable String ruleSet) {
  public AgentConfiguration(AgentKind kind, String preset, Map<String, Double> settings) {
    this(kind, preset, settings, null);
  }

  public AgentConfiguration {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(preset);
    settings = Map.copyOf(settings);
  }
}
