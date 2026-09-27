package lmarek.lcs.arena;

public record CompetitorView(
    String id, String label, String kind, boolean learns, boolean ruleInspectorAvailable) {}
