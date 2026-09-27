package lmarek.lcs.arena;

public record SettingOption(
    String id,
    String label,
    double defaultValue,
    double minimum,
    double maximum,
    double step,
    boolean integral,
    boolean even) {}
