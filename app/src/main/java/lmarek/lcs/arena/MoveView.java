package lmarek.lcs.arena;

import java.util.List;
import lmarek.lcs.draughts.DraughtsMove;

public record MoveView(
    int origin,
    List<Integer> landings,
    List<Integer> capturedSquares,
    String actionId,
    String notation) {
  public MoveView {
    landings = List.copyOf(landings);
    capturedSquares = List.copyOf(capturedSquares);
  }

  static MoveView from(DraughtsMove move) {
    return new MoveView(
        move.origin(), move.landings(), move.capturedSquares(), move.actionId(), notation(move));
  }

  private static String notation(DraughtsMove move) {
    var separator = move.isCapture() ? " × " : " – ";
    return move.origin()
        + separator
        + String.join(separator, move.landings().stream().map(Object::toString).toList());
  }
}
