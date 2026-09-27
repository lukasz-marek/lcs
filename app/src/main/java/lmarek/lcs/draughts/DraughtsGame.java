package lmarek.lcs.draughts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.PositionAnalysis;
import lmarek.lcs.game.TerminationReason;
import lmarek.lcs.game.TurnBasedGame;

/** FMJD international draughts rules on the standard 10x10 board. */
public final class DraughtsGame implements TurnBasedGame<DraughtsState, DraughtsMove> {
  private static final int[][] DIAGONALS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

  @Override
  public DraughtsState initialState() {
    return DraughtsState.initial();
  }

  @Override
  public Player playerToMove(DraughtsState state) {
    return state.playerToMove();
  }

  @Override
  public List<DraughtsMove> legalMoves(DraughtsState state) {
    Objects.requireNonNull(state);
    var captures = captureMoves(state);
    if (!captures.isEmpty()) {
      var maximum =
          captures.stream().mapToInt(move -> move.capturedSquares().size()).max().orElseThrow();
      return captures.stream().filter(move -> move.capturedSquares().size() == maximum).toList();
    }
    return quietMoves(state);
  }

  /** Legal moves keyed by their board-absolute stable action ID. */
  public Map<String, DraughtsMove> legalMovesByActionId(DraughtsState state) {
    var result = new LinkedHashMap<String, DraughtsMove>();
    legalMoves(state).forEach(move -> result.put(move.actionId(), move));
    return Collections.unmodifiableMap(result);
  }

  /** Legal moves keyed after rotating Black's viewpoint so both players advance upward. */
  public Map<String, DraughtsMove> legalMovesByActorRelativeActionId(DraughtsState state) {
    var result = new LinkedHashMap<String, DraughtsMove>();
    legalMoves(state)
        .forEach(move -> result.put(move.orientedFor(state.playerToMove()).actionId(), move));
    return Collections.unmodifiableMap(result);
  }

  public Optional<DraughtsMove> findLegalMove(DraughtsState state, String actionId) {
    return Optional.ofNullable(legalMovesByActionId(state).get(actionId));
  }

  public Optional<DraughtsMove> findActorRelativeLegalMove(DraughtsState state, String actionId) {
    return Optional.ofNullable(legalMovesByActorRelativeActionId(state).get(actionId));
  }

  @Override
  public DraughtsState applyMove(DraughtsState state, DraughtsMove requestedMove) {
    Objects.requireNonNull(state);
    Objects.requireNonNull(requestedMove);
    var move =
        legalMoves(state).stream()
            .filter(requestedMove::equals)
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Illegal draughts move: " + requestedMove));
    return applyLegalMove(state, move);
  }

  @Override
  public DraughtsState applyAnalyzedMove(
      PositionAnalysis<DraughtsState, DraughtsMove> analysis, DraughtsMove move) {
    return applyLegalMove(analysis.validate(this, move), move);
  }

  private DraughtsState applyLegalMove(DraughtsState state, DraughtsMove move) {
    var player = state.playerToMove();
    var originBit = DraughtsBoard.bit(move.origin());
    var destinationBit = DraughtsBoard.bit(move.destination());
    var movingMan = (state.men(player) & originBit) != 0;
    var captures = bits(move.capturedSquares());

    var whiteMen = state.whiteMen();
    var whiteKings = state.whiteKings();
    var blackMen = state.blackMen();
    var blackKings = state.blackKings();
    if (player == Player.WHITE) {
      whiteMen &= ~originBit;
      whiteKings &= ~originBit;
      blackMen &= ~captures;
      blackKings &= ~captures;
      if (movingMan && !DraughtsBoard.isPromotionRow(move.destination(), true)) {
        whiteMen |= destinationBit;
      } else {
        whiteKings |= destinationBit;
      }
    } else {
      blackMen &= ~originBit;
      blackKings &= ~originBit;
      whiteMen &= ~captures;
      whiteKings &= ~captures;
      if (movingMan && !DraughtsBoard.isPromotionRow(move.destination(), false)) {
        blackMen |= destinationBit;
      } else {
        blackKings |= destinationBit;
      }
    }

    var nextPlayer = player.opponent();
    var nextSignature =
        new PositionSignature(whiteMen, whiteKings, blackMen, blackKings, nextPlayer);
    var irreversible = movingMan || move.isCapture();
    var repetitions =
        irreversible
            ? RepetitionHistory.start(nextSignature)
            : state.repetitionHistory().append(nextSignature);
    var whiteKingOnlyMoves =
        irreversible ? 0 : state.whiteKingOnlyMoves() + (player == Player.WHITE ? 1 : 0);
    var blackKingOnlyMoves =
        irreversible ? 0 : state.blackKingOnlyMoves() + (player == Player.BLACK ? 1 : 0);

    var provisional =
        new DraughtsState(
            whiteMen,
            whiteKings,
            blackMen,
            blackKings,
            nextPlayer,
            repetitions,
            whiteKingOnlyMoves,
            blackKingOnlyMoves,
            List.of(),
            state.ply() + 1);
    var limitedClocks = new ArrayList<LimitedEndgameClock>();
    for (var clock : state.limitedEndgameClocks()) {
      if (clock.remainsApplicable(provisional)) {
        limitedClocks.add(clock.afterMove(player, provisional));
      }
    }
    var newlyApplicable =
        LimitedEndgameClock.forPosition(whiteMen, whiteKings, blackMen, blackKings);
    if (newlyApplicable.isPresent()
        && limitedClocks.stream()
            .noneMatch(clock -> clock.kind() == newlyApplicable.orElseThrow().kind())) {
      limitedClocks.add(newlyApplicable.orElseThrow());
    }
    return new DraughtsState(
        whiteMen,
        whiteKings,
        blackMen,
        blackKings,
        nextPlayer,
        repetitions,
        whiteKingOnlyMoves,
        blackKingOnlyMoves,
        limitedClocks,
        state.ply() + 1);
  }

  @Override
  public Optional<GameOutcome> outcome(DraughtsState state) {
    return outcomeFromLegalMoves(state, legalMoves(state));
  }

  @Override
  public Optional<GameOutcome> outcomeFromLegalMoves(
      DraughtsState state, List<DraughtsMove> legalMoves) {
    Objects.requireNonNull(state);
    var player = state.playerToMove();
    if (state.pieces(player) == 0) {
      return Optional.of(GameOutcome.win(player.opponent(), TerminationReason.NO_PIECES));
    }
    if (legalMoves.isEmpty()) {
      return Optional.of(GameOutcome.win(player.opponent(), TerminationReason.NO_LEGAL_MOVES));
    }
    if (state.currentRepetitionCount() >= 3) {
      return Optional.of(GameOutcome.draw(TerminationReason.THREEFOLD_REPETITION));
    }
    if (state.limitedEndgameClocks().stream().anyMatch(LimitedEndgameClock::expired)) {
      return Optional.of(GameOutcome.draw(TerminationReason.LIMITED_ENDGAME_MOVE_LIMIT));
    }
    if (state.whiteKingOnlyMoves() >= 25 && state.blackKingOnlyMoves() >= 25) {
      return Optional.of(GameOutcome.draw(TerminationReason.KING_ONLY_MOVE_LIMIT));
    }
    return Optional.empty();
  }

  private List<DraughtsMove> captureMoves(DraughtsState state) {
    var result = new LinkedHashSet<DraughtsMove>();
    var men = state.men(state.playerToMove());
    while (men != 0) {
      var origin = Long.numberOfTrailingZeros(men) + 1;
      collectManCaptures(state, origin, origin, 0, new ArrayList<>(), new ArrayList<>(), result);
      men &= men - 1;
    }
    var kings = state.kings(state.playerToMove());
    while (kings != 0) {
      var origin = Long.numberOfTrailingZeros(kings) + 1;
      collectKingCaptures(state, origin, origin, 0, new ArrayList<>(), new ArrayList<>(), result);
      kings &= kings - 1;
    }
    return List.copyOf(result);
  }

  private void collectManCaptures(
      DraughtsState state,
      int origin,
      int current,
      long alreadyCaptured,
      List<Integer> landings,
      List<Integer> capturedSquares,
      Set<DraughtsMove> result) {
    var continued = false;
    var row = DraughtsBoard.rowOf(current);
    var column = DraughtsBoard.columnOf(current);
    var occupied = movingOccupancy(state, origin, current);
    var opponents = state.pieces(state.playerToMove().opponent());
    for (var direction : DIAGONALS) {
      var jumped = DraughtsBoard.squareAt(row + direction[0], column + direction[1]);
      var landing = DraughtsBoard.squareAt(row + 2 * direction[0], column + 2 * direction[1]);
      if (jumped.isEmpty() || landing.isEmpty()) {
        continue;
      }
      var jumpedBit = DraughtsBoard.bit(jumped.getAsInt());
      var landingBit = DraughtsBoard.bit(landing.getAsInt());
      if ((opponents & jumpedBit) == 0
          || (alreadyCaptured & jumpedBit) != 0
          || (occupied & landingBit) != 0) {
        continue;
      }
      continued = true;
      landings.add(landing.getAsInt());
      capturedSquares.add(jumped.getAsInt());
      collectManCaptures(
          state,
          origin,
          landing.getAsInt(),
          alreadyCaptured | jumpedBit,
          landings,
          capturedSquares,
          result);
      landings.removeLast();
      capturedSquares.removeLast();
    }
    if (!continued && !capturedSquares.isEmpty()) {
      result.add(DraughtsMove.capture(origin, landings, capturedSquares));
    }
  }

  private void collectKingCaptures(
      DraughtsState state,
      int origin,
      int current,
      long alreadyCaptured,
      List<Integer> landings,
      List<Integer> capturedSquares,
      Set<DraughtsMove> result) {
    var continued = false;
    var occupied = movingOccupancy(state, origin, current);
    var opponents = state.pieces(state.playerToMove().opponent());
    for (var direction : DIAGONALS) {
      var row = DraughtsBoard.rowOf(current) + direction[0];
      var column = DraughtsBoard.columnOf(current) + direction[1];
      var encountered = 0;
      while (true) {
        var square = DraughtsBoard.squareAt(row, column);
        if (square.isEmpty()) {
          break;
        }
        var squareBit = DraughtsBoard.bit(square.getAsInt());
        if ((occupied & squareBit) != 0) {
          encountered = square.getAsInt();
          break;
        }
        row += direction[0];
        column += direction[1];
      }
      if (encountered == 0) {
        continue;
      }
      var encounteredBit = DraughtsBoard.bit(encountered);
      if ((opponents & encounteredBit) == 0 || (alreadyCaptured & encounteredBit) != 0) {
        continue;
      }
      row = DraughtsBoard.rowOf(encountered) + direction[0];
      column = DraughtsBoard.columnOf(encountered) + direction[1];
      while (true) {
        var landing = DraughtsBoard.squareAt(row, column);
        if (landing.isEmpty()) {
          break;
        }
        var landingBit = DraughtsBoard.bit(landing.getAsInt());
        if ((occupied & landingBit) != 0) {
          break;
        }
        continued = true;
        landings.add(landing.getAsInt());
        capturedSquares.add(encountered);
        collectKingCaptures(
            state,
            origin,
            landing.getAsInt(),
            alreadyCaptured | encounteredBit,
            landings,
            capturedSquares,
            result);
        landings.removeLast();
        capturedSquares.removeLast();
        row += direction[0];
        column += direction[1];
      }
    }
    if (!continued && !capturedSquares.isEmpty()) {
      result.add(DraughtsMove.capture(origin, landings, capturedSquares));
    }
  }

  private List<DraughtsMove> quietMoves(DraughtsState state) {
    var result = new ArrayList<DraughtsMove>();
    var occupied = state.occupied();
    var men = state.men(state.playerToMove());
    var rowDirection = state.playerToMove() == Player.WHITE ? -1 : 1;
    while (men != 0) {
      var origin = Long.numberOfTrailingZeros(men) + 1;
      var row = DraughtsBoard.rowOf(origin);
      var column = DraughtsBoard.columnOf(origin);
      for (var columnDirection : new int[] {-1, 1}) {
        var destination = DraughtsBoard.squareAt(row + rowDirection, column + columnDirection);
        if (destination.isPresent()
            && (occupied & DraughtsBoard.bit(destination.getAsInt())) == 0) {
          result.add(DraughtsMove.quiet(origin, destination.getAsInt()));
        }
      }
      men &= men - 1;
    }
    var kings = state.kings(state.playerToMove());
    while (kings != 0) {
      var origin = Long.numberOfTrailingZeros(kings) + 1;
      for (var direction : DIAGONALS) {
        var row = DraughtsBoard.rowOf(origin) + direction[0];
        var column = DraughtsBoard.columnOf(origin) + direction[1];
        while (true) {
          var destination = DraughtsBoard.squareAt(row, column);
          if (destination.isEmpty()
              || (occupied & DraughtsBoard.bit(destination.getAsInt())) != 0) {
            break;
          }
          result.add(DraughtsMove.quiet(origin, destination.getAsInt()));
          row += direction[0];
          column += direction[1];
        }
      }
      kings &= kings - 1;
    }
    return List.copyOf(result);
  }

  private static long movingOccupancy(DraughtsState state, int origin, int current) {
    return (state.occupied() & ~DraughtsBoard.bit(origin)) | DraughtsBoard.bit(current);
  }

  private static long bits(List<Integer> squares) {
    var result = 0L;
    for (var square : squares) {
      result |= DraughtsBoard.bit(square);
    }
    return result;
  }
}
