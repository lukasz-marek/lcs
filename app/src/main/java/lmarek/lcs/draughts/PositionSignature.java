package lmarek.lcs.draughts;

import lmarek.lcs.game.Player;

/** Collision-free identity used by the threefold-repetition rule. */
public record PositionSignature(
    long whiteMen, long whiteKings, long blackMen, long blackKings, Player playerToMove) {}
