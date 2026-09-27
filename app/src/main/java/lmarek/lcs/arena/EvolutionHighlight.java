package lmarek.lcs.arena;

public record EvolutionHighlight(
    long gameNumber, String competitorId, long ruleId, String type, String message) {}
