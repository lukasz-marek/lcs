package lmarek.lcs.draughts;

import java.util.Objects;
import java.util.Optional;
import lmarek.lcs.game.Player;

/** Progress through an FMJD five- or sixteen-move material ending. */
public record LimitedEndgameClock(
    LimitedEndgameKind kind,
    Player strongerSide,
    int whiteMoves,
    int blackMoves,
    boolean longDiagonalExtension) {
  private static final long LONG_DIAGONAL = mask(5, 10, 14, 19, 23, 28, 32, 37, 41, 46);

  public LimitedEndgameClock {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(strongerSide);
    if (whiteMoves < 0 || blackMoves < 0) {
      throw new IllegalArgumentException("Endgame move counts cannot be negative");
    }
    if (kind == LimitedEndgameKind.FIVE_MOVES && longDiagonalExtension) {
      throw new IllegalArgumentException("Only a sixteen-move ending has an extension");
    }
  }

  static Optional<LimitedEndgameClock> forPosition(
      long whiteMen, long whiteKings, long blackMen, long blackKings) {
    var white = classify(whiteMen, whiteKings, blackMen, blackKings, Player.WHITE);
    if (white.isPresent()) {
      return white;
    }
    return classify(blackMen, blackKings, whiteMen, whiteKings, Player.BLACK);
  }

  private static Optional<LimitedEndgameClock> classify(
      long strongMen, long strongKings, long weakMen, long weakKings, Player strongSide) {
    if (weakMen != 0 || Long.bitCount(weakKings) != 1) {
      return Optional.empty();
    }
    var men = Long.bitCount(strongMen);
    var kings = Long.bitCount(strongKings);
    LimitedEndgameKind kind;
    if ((kings == 3 && men == 0) || (kings == 2 && men == 1) || (kings == 1 && men == 2)) {
      kind = LimitedEndgameKind.SIXTEEN_MOVES;
    } else if ((kings == 2 && men == 0) || (kings == 1 && men == 1) || (kings == 1 && men == 0)) {
      kind = LimitedEndgameKind.FIVE_MOVES;
    } else {
      return Optional.empty();
    }
    // King against king matches from both sides. White is the canonical clock owner.
    if (kind == LimitedEndgameKind.FIVE_MOVES
        && men == 0
        && kings == 1
        && strongSide == Player.BLACK) {
      return Optional.empty();
    }
    return Optional.of(new LimitedEndgameClock(kind, strongSide, 0, 0, false));
  }

  LimitedEndgameClock afterMove(Player player, DraughtsState stateAfterMove) {
    var nextWhiteMoves = whiteMoves + (player == Player.WHITE ? 1 : 0);
    var nextBlackMoves = blackMoves + (player == Player.BLACK ? 1 : 0);
    var extended = longDiagonalExtension;
    if (kind == LimitedEndgameKind.SIXTEEN_MOVES
        && !extended
        && nextWhiteMoves >= 16
        && nextBlackMoves >= 16) {
      var weakKings =
          strongerSide == Player.WHITE ? stateAfterMove.blackKings() : stateAfterMove.whiteKings();
      extended = (weakKings & LONG_DIAGONAL) != 0;
    }
    return new LimitedEndgameClock(kind, strongerSide, nextWhiteMoves, nextBlackMoves, extended);
  }

  boolean remainsApplicable(DraughtsState state) {
    var weakMen = strongerSide == Player.WHITE ? state.blackMen() : state.whiteMen();
    var weakKings = strongerSide == Player.WHITE ? state.blackKings() : state.whiteKings();
    var strongMen = strongerSide == Player.WHITE ? state.whiteMen() : state.blackMen();
    var strongKings = strongerSide == Player.WHITE ? state.whiteKings() : state.blackKings();
    var strongPieces = Long.bitCount(strongMen | strongKings);
    if (weakMen != 0 || Long.bitCount(weakKings) != 1 || strongPieces == 0) {
      return false;
    }
    return kind == LimitedEndgameKind.SIXTEEN_MOVES
        ? strongPieces <= 3
        : strongPieces <= 2 && strongKings != 0;
  }

  public int currentLimit() {
    return kind.movesPerPlayer() + (longDiagonalExtension ? 5 : 0);
  }

  public boolean expired() {
    return whiteMoves >= currentLimit() && blackMoves >= currentLimit();
  }

  private static long mask(int... squares) {
    var result = 0L;
    for (var square : squares) {
      result |= DraughtsBoard.bit(square);
    }
    return result;
  }
}
