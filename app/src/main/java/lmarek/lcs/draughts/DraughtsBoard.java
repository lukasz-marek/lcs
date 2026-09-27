package lmarek.lcs.draughts;

import java.util.OptionalInt;

/** Coordinate and bit-board helpers for the 50 playable squares of a 10x10 board. */
public final class DraughtsBoard {
  public static final int BOARD_SIZE = 10;
  public static final int PLAYABLE_SQUARES = 50;
  public static final long BOARD_MASK = (1L << PLAYABLE_SQUARES) - 1L;

  private DraughtsBoard() {}

  public static int rowOf(int square) {
    requireSquare(square);
    return (square - 1) / 5;
  }

  public static int columnOf(int square) {
    var row = rowOf(square);
    var indexInRow = (square - 1) % 5;
    return indexInRow * 2 + (row % 2 == 0 ? 1 : 0);
  }

  public static OptionalInt squareAt(int row, int column) {
    if (row < 0
        || row >= BOARD_SIZE
        || column < 0
        || column >= BOARD_SIZE
        || (row + column) % 2 == 0) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(row * 5 + column / 2 + 1);
  }

  public static int squareAtRequired(int row, int column) {
    return squareAt(row, column)
        .orElseThrow(() -> new IllegalArgumentException("Not a playable board coordinate"));
  }

  public static long bit(int square) {
    requireSquare(square);
    return 1L << (square - 1);
  }

  /** Rotates the board by 180 degrees, preserving standard draughts numbering. */
  public static int rotate(int square) {
    requireSquare(square);
    return 51 - square;
  }

  public static boolean isPromotionRow(int square, boolean white) {
    return rowOf(square) == (white ? 0 : 9);
  }

  public static void requireSquare(int square) {
    if (square < 1 || square > PLAYABLE_SQUARES) {
      throw new IllegalArgumentException("Square must be between 1 and 50: " + square);
    }
  }
}
