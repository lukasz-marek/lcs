package lmarek.lcs.arena;

import java.util.Map;

public record PresetOption(String id, String label, Map<String, Double> settings) {
  public PresetOption {
    settings = Map.copyOf(settings);
  }
}
