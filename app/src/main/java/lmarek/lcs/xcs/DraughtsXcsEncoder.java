package lmarek.lcs.xcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lmarek.lcs.draughts.DraughtsBoard;
import lmarek.lcs.draughts.DraughtsGame;
import lmarek.lcs.draughts.DraughtsMove;
import lmarek.lcs.draughts.DraughtsState;
import lmarek.lcs.draughts.LimitedEndgameClock;
import lmarek.lcs.draughts.LimitedEndgameKind;
import lmarek.lcs.draughts.PositionSignature;
import lmarek.lcs.game.Player;

/** Actor-relative categorical encoding for 10x10 international draughts. */
public final class DraughtsXcsEncoder implements StateActionEncoder<DraughtsState, DraughtsMove> {
  public static final int BOARD_ATTRIBUTE_COUNT = 50;

  @Override
  public CategoricalState encode(DraughtsState state, Player perspective) {
    var values = new ArrayList<String>(64);
    for (var relativeSquare = 1;
        relativeSquare <= DraughtsBoard.PLAYABLE_SQUARES;
        relativeSquare++) {
      var absoluteSquare =
          perspective == Player.WHITE ? relativeSquare : DraughtsBoard.rotate(relativeSquare);
      values.add(pieceAt(state, absoluteSquare, perspective));
    }
    values.add("TO_MOVE_" + (state.playerToMove() == perspective ? "SELF" : "OPPONENT"));
    values.add(repetitionContext(state, perspective));
    values.add(
        "SELF_KING_ONLY_MOVES_"
            + (perspective == Player.WHITE
                ? state.whiteKingOnlyMoves()
                : state.blackKingOnlyMoves()));
    values.add(
        "OPPONENT_KING_ONLY_MOVES_"
            + (perspective == Player.WHITE
                ? state.blackKingOnlyMoves()
                : state.whiteKingOnlyMoves()));
    for (var kind : LimitedEndgameKind.values()) {
      addLimitedClockContext(
          values,
          kind,
          state.limitedEndgameClocks().stream().filter(clock -> clock.kind() == kind).findFirst(),
          perspective,
          isKingAgainstKing(state));
    }
    return new CategoricalState(values);
  }

  @Override
  public String actionId(DraughtsMove move, Player perspective) {
    return move.orientedFor(perspective).actionId();
  }

  /** Builds the only map through which a learned action should be resolved to a move. */
  public Map<String, DraughtsMove> legalActionMap(
      DraughtsGame game, DraughtsState state, Player perspective) {
    var result = new LinkedHashMap<String, DraughtsMove>();
    for (var move : game.legalMoves(state)) {
      var previous = result.put(actionId(move, perspective), move);
      if (previous != null) {
        throw new IllegalStateException("Two legal draughts moves have the same XCS action ID");
      }
    }
    return Map.copyOf(result);
  }

  public Optional<DraughtsMove> resolveLegal(
      DraughtsGame game, DraughtsState state, Player perspective, String actionId) {
    return Optional.ofNullable(legalActionMap(game, state, perspective).get(actionId));
  }

  private static String pieceAt(DraughtsState state, int square, Player perspective) {
    var bit = DraughtsBoard.bit(square);
    if ((state.men(perspective) & bit) != 0) {
      return "SELF_MAN";
    }
    if ((state.kings(perspective) & bit) != 0) {
      return "SELF_KING";
    }
    if ((state.men(perspective.opponent()) & bit) != 0) {
      return "OPPONENT_MAN";
    }
    if ((state.kings(perspective.opponent()) & bit) != 0) {
      return "OPPONENT_KING";
    }
    return "EMPTY";
  }

  private static void addLimitedClockContext(
      List<String> values,
      LimitedEndgameKind kind,
      Optional<LimitedEndgameClock> clock,
      Player perspective,
      boolean equalKingEnding) {
    var prefix = "LIMITED_" + kind.name();
    if (clock.isEmpty()) {
      values.add(prefix + "_ACTIVE_FALSE");
      values.add(prefix + "_STRONGER_NONE");
      values.add(prefix + "_SELF_MOVES_NONE");
      values.add(prefix + "_OPPONENT_MOVES_NONE");
      values.add(prefix + "_EXTENSION_NONE");
      return;
    }
    var active = clock.orElseThrow();
    values.add(prefix + "_ACTIVE_TRUE");
    values.add(
        prefix
            + "_STRONGER_"
            + (equalKingEnding && kind == LimitedEndgameKind.FIVE_MOVES
                ? "EQUAL"
                : (active.strongerSide() == perspective ? "SELF" : "OPPONENT")));
    values.add(
        prefix
            + "_SELF_MOVES_"
            + (perspective == Player.WHITE ? active.whiteMoves() : active.blackMoves()));
    values.add(
        prefix
            + "_OPPONENT_MOVES_"
            + (perspective == Player.WHITE ? active.blackMoves() : active.whiteMoves()));
    values.add(prefix + "_EXTENSION_" + active.longDiagonalExtension());
  }

  /**
   * Encodes the complete occurrence map for the current reversible window. Fixed-width masks and
   * explicit separators make this categorical value collision-free without sacrificing an
   * observation attribute.
   */
  private static String repetitionContext(DraughtsState state, Player perspective) {
    var occurrences = new HashMap<PositionSignature, Integer>();
    for (var entry = Optional.of(state.repetitionHistory()); entry.isPresent(); ) {
      var history = entry.orElseThrow();
      occurrences.merge(history.signature(), 1, Integer::sum);
      entry = history.previous();
    }
    var encoded =
        occurrences.entrySet().stream()
            .map(
                entry ->
                    actorRelativeSignature(entry.getKey(), perspective) + "=" + entry.getValue())
            .sorted()
            .toList();
    return "REPETITION_HISTORY[" + String.join(",", encoded) + "]";
  }

  private static String actorRelativeSignature(PositionSignature signature, Player perspective) {
    long selfMen;
    long selfKings;
    long opponentMen;
    long opponentKings;
    if (perspective == Player.WHITE) {
      selfMen = signature.whiteMen();
      selfKings = signature.whiteKings();
      opponentMen = signature.blackMen();
      opponentKings = signature.blackKings();
    } else {
      selfMen = rotate(signature.blackMen());
      selfKings = rotate(signature.blackKings());
      opponentMen = rotate(signature.whiteMen());
      opponentKings = rotate(signature.whiteKings());
    }
    var relativeTurn = signature.playerToMove() == perspective ? 'S' : 'O';
    return "%013x:%013x:%013x:%013x:%c"
        .formatted(selfMen, selfKings, opponentMen, opponentKings, relativeTurn);
  }

  private static long rotate(long squares) {
    var rotated = 0L;
    while (squares != 0) {
      var square = Long.numberOfTrailingZeros(squares) + 1;
      rotated |= DraughtsBoard.bit(DraughtsBoard.rotate(square));
      squares &= squares - 1;
    }
    return rotated;
  }

  private static boolean isKingAgainstKing(DraughtsState state) {
    return state.whiteMen() == 0
        && state.blackMen() == 0
        && Long.bitCount(state.whiteKings()) == 1
        && Long.bitCount(state.blackKings()) == 1;
  }
}
