package lmarek.lcs.arena;

import java.util.ArrayList;
import java.util.List;
import lmarek.lcs.draughts.DraughtsState;

public record BoardView(
    List<Integer> whiteMen,
    List<Integer> whiteKings,
    List<Integer> blackMen,
    List<Integer> blackKings) {
  public BoardView {
    whiteMen = List.copyOf(whiteMen);
    whiteKings = List.copyOf(whiteKings);
    blackMen = List.copyOf(blackMen);
    blackKings = List.copyOf(blackKings);
  }

  static BoardView from(DraughtsState state) {
    return new BoardView(
        squares(state.whiteMen()),
        squares(state.whiteKings()),
        squares(state.blackMen()),
        squares(state.blackKings()));
  }

  private static List<Integer> squares(long bits) {
    var result = new ArrayList<Integer>();
    while (bits != 0) {
      result.add(Long.numberOfTrailingZeros(bits) + 1);
      bits &= bits - 1;
    }
    return List.copyOf(result);
  }
}
