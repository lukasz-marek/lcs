package lmarek.lcs.arena;

import org.jspecify.annotations.Nullable;

public record ActionScoreView(
    String actionId,
    @Nullable Double prediction,
    @Nullable Long visits,
    @Nullable Double meanValue) {}
