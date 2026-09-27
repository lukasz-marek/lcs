package lmarek.lcs.arena;

import org.jspecify.annotations.Nullable;

public record CurrentGameView(
    long gameNumber,
    boolean evaluation,
    String whiteCompetitorId,
    String blackCompetitorId,
    String whiteLabel,
    String blackLabel,
    String whiteKind,
    String blackKind,
    int ply,
    BoardView board,
    @Nullable MoveView lastMove,
    @Nullable DecisionTraceView lastTrace,
    @Nullable String lastAgentKind) {}
