package lmarek.lcs.arena;

public record ArenaStatisticsView(
    long aWins,
    long bWins,
    long draws,
    String rollingLabel,
    double averageLength,
    double gamesPerSecond) {}
