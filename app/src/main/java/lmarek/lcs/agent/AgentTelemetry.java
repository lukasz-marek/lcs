package lmarek.lcs.agent;

import java.util.Map;

public record AgentTelemetry(Map<String, Double> metrics) {
  public AgentTelemetry {
    metrics = Map.copyOf(metrics);
  }

  public static AgentTelemetry empty() {
    return new AgentTelemetry(Map.of());
  }
}
