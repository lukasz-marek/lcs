package lmarek.lcs.arena;

import java.util.List;

public record AgentKindOption(
    String id,
    String label,
    String defaultPreset,
    List<PresetOption> presets,
    List<SettingOption> settings) {
  public AgentKindOption {
    presets = List.copyOf(presets);
    settings = List.copyOf(settings);
  }
}
