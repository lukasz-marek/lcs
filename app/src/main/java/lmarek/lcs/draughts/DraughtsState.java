package lmarek.lcs.draughts;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lmarek.lcs.game.Player;

/** Immutable international-draughts position and exact rule clocks. */
public record DraughtsState(
    long whiteMen,
    long whiteKings,
    long blackMen,
    long blackKings,
    Player playerToMove,
    RepetitionHistory repetitionHistory,
    int whiteKingOnlyMoves,
    int blackKingOnlyMoves,
    List<LimitedEndgameClock> limitedEndgameClocks,
    long ply) {
  public DraughtsState {
    Objects.requireNonNull(playerToMove);
    Objects.requireNonNull(repetitionHistory);
    limitedEndgameClocks = List.copyOf(limitedEndgameClocks);
    var occupied = whiteMen | whiteKings | blackMen | blackKings;
    if ((occupied & ~DraughtsBoard.BOARD_MASK) != 0) {
      throw new IllegalArgumentException("A piece lies outside the 50-square board");
    }
    var totalPieces =
        Long.bitCount(whiteMen)
            + Long.bitCount(whiteKings)
            + Long.bitCount(blackMen)
            + Long.bitCount(blackKings);
    if (Long.bitCount(occupied) != totalPieces) {
      throw new IllegalArgumentException("Piece bitboards overlap");
    }
    if (whiteKingOnlyMoves < 0 || blackKingOnlyMoves < 0 || ply < 0) {
      throw new IllegalArgumentException("Move counters cannot be negative");
    }
    if (!repetitionHistory
        .signature()
        .equals(signatureOf(whiteMen, whiteKings, blackMen, blackKings, playerToMove))) {
      throw new IllegalArgumentException("Repetition history does not end at this position");
    }
  }

  public static DraughtsState initial() {
    return position(rangeMask(31, 50), 0, rangeMask(1, 20), 0, Player.WHITE);
  }

  /** Creates a position with fresh draw clocks, primarily for analysis and tests. */
  public static DraughtsState position(
      long whiteMen, long whiteKings, long blackMen, long blackKings, Player playerToMove) {
    var signature = signatureOf(whiteMen, whiteKings, blackMen, blackKings, playerToMove);
    return new DraughtsState(
        whiteMen,
        whiteKings,
        blackMen,
        blackKings,
        playerToMove,
        RepetitionHistory.start(signature),
        0,
        0,
        LimitedEndgameClock.forPosition(whiteMen, whiteKings, blackMen, blackKings).stream()
            .toList(),
        0);
  }

  /** Compatibility view of the first clock; use {@link #limitedEndgameClocks()} for exact rules. */
  public Optional<LimitedEndgameClock> limitedEndgameClock() {
    return limitedEndgameClocks.stream().findFirst();
  }

  public static long squares(int... squares) {
    var result = 0L;
    for (var square : squares) {
      result |= DraughtsBoard.bit(square);
    }
    return result;
  }

  public long occupied() {
    return whiteMen | whiteKings | blackMen | blackKings;
  }

  public long pieces(Player player) {
    return player == Player.WHITE ? whiteMen | whiteKings : blackMen | blackKings;
  }

  public long men(Player player) {
    return player == Player.WHITE ? whiteMen : blackMen;
  }

  public long kings(Player player) {
    return player == Player.WHITE ? whiteKings : blackKings;
  }

  public int currentRepetitionCount() {
    return repetitionHistory.currentOccurrences();
  }

  PositionSignature signature() {
    return signatureOf(whiteMen, whiteKings, blackMen, blackKings, playerToMove);
  }

  private static PositionSignature signatureOf(
      long whiteMen, long whiteKings, long blackMen, long blackKings, Player playerToMove) {
    return new PositionSignature(whiteMen, whiteKings, blackMen, blackKings, playerToMove);
  }

  private static long rangeMask(int first, int last) {
    var result = 0L;
    for (var square = first; square <= last; square++) {
      result |= DraughtsBoard.bit(square);
    }
    return result;
  }
}
