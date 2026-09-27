package lmarek.lcs.draughts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.TerminationReason;
import org.junit.jupiter.api.Test;

class DraughtsOutcomeTest {
  private final DraughtsGame game = new DraughtsGame();

  @Test
  void playerLosesWhenTheyHaveNoPiecesOrNoLegalMove() {
    var noPieces = DraughtsState.position(0, 0, DraughtsState.squares(1), 0, Player.WHITE);
    var immobilized =
        DraughtsState.position(
            DraughtsState.squares(1), 0, DraughtsState.squares(46), 0, Player.BLACK);

    assertThat(game.outcome(noPieces).orElseThrow().winner()).isEqualTo(Player.BLACK);
    assertThat(game.outcome(noPieces).orElseThrow().reason())
        .isEqualTo(TerminationReason.NO_PIECES);
    assertThat(game.outcome(immobilized).orElseThrow().winner()).isEqualTo(Player.WHITE);
    assertThat(game.outcome(immobilized).orElseThrow().reason())
        .isEqualTo(TerminationReason.NO_LEGAL_MOVES);
  }

  @Test
  void thirdExactPositionWithTheSameSideToMoveIsADraw() {
    var state =
        DraughtsState.position(
            0, DraughtsState.squares(46), 0, DraughtsState.squares(1), Player.WHITE);
    var firstHistory = state.repetitionHistory();

    for (var cycle = 0; cycle < 2; cycle++) {
      state = game.applyMove(state, DraughtsMove.quiet(46, 41));
      state = game.applyMove(state, DraughtsMove.quiet(1, 6));
      state = game.applyMove(state, DraughtsMove.quiet(41, 46));
      state = game.applyMove(state, DraughtsMove.quiet(6, 1));
    }

    assertThat(state.currentRepetitionCount()).isEqualTo(3);
    assertThat(state.repetitionHistory().size()).isEqualTo(9);
    assertThat(state.repetitionHistory().previous()).isPresent();
    assertThat(historyContains(state.repetitionHistory(), firstHistory)).isTrue();
    assertThat(game.outcome(state).orElseThrow().reason())
        .isEqualTo(TerminationReason.THREEFOLD_REPETITION);
  }

  @Test
  void aManMoveResetsTheStructurallySharedRepetitionWindow() {
    var state =
        DraughtsState.position(
            DraughtsState.squares(31),
            DraughtsState.squares(46),
            DraughtsState.squares(20),
            DraughtsState.squares(1),
            Player.WHITE);

    var next = game.applyMove(state, DraughtsMove.quiet(31, 26));

    assertThat(next.repetitionHistory().size()).isOne();
    assertThat(next.repetitionHistory().previous()).isEmpty();
    assertThat(next.whiteKingOnlyMoves()).isZero();
    assertThat(next.blackKingOnlyMoves()).isZero();
  }

  @Test
  void twentyFiveQualifyingMovesForEachPlayerIsADraw() {
    var base =
        DraughtsState.position(
            DraughtsState.squares(50),
            DraughtsState.squares(46),
            DraughtsState.squares(2),
            DraughtsState.squares(1),
            Player.WHITE);
    var state = withDrawClocks(base, 24, 24, Optional.empty());

    state = game.applyMove(state, DraughtsMove.quiet(46, 41));
    assertThat(game.outcome(state)).isEmpty();
    state = game.applyMove(state, DraughtsMove.quiet(1, 6));

    assertThat(game.outcome(state).orElseThrow().reason())
        .isEqualTo(TerminationReason.KING_ONLY_MOVE_LIMIT);
  }

  @Test
  void fiveMoveEndingExpiresOnlyAfterBothPlayersMoveFiveTimes() {
    var base =
        DraughtsState.position(
            0, DraughtsState.squares(46), 0, DraughtsState.squares(1), Player.WHITE);
    var clock = new LimitedEndgameClock(LimitedEndgameKind.FIVE_MOVES, Player.WHITE, 4, 4, false);
    var state = withDrawClocks(base, 4, 4, Optional.of(clock));

    state = game.applyMove(state, DraughtsMove.quiet(46, 41));
    assertThat(game.outcome(state)).isEmpty();
    state = game.applyMove(state, DraughtsMove.quiet(1, 6));

    assertThat(game.outcome(state).orElseThrow().reason())
        .isEqualTo(TerminationReason.LIMITED_ENDGAME_MOVE_LIMIT);
  }

  @Test
  void sixteenMoveEndingGetsFiveMoreMovesWhenTheLoneKingReachesTheLongDiagonal() {
    var base =
        DraughtsState.position(
            0, DraughtsState.squares(48, 49, 50), 0, DraughtsState.squares(10), Player.WHITE);
    var clock =
        new LimitedEndgameClock(LimitedEndgameKind.SIXTEEN_MOVES, Player.WHITE, 15, 15, false);
    var state = withDrawClocks(base, 15, 15, Optional.of(clock));

    state = game.applyMove(state, DraughtsMove.quiet(48, 43));
    state = game.applyMove(state, DraughtsMove.quiet(10, 5));

    assertThat(state.limitedEndgameClock()).isPresent();
    assertThat(state.limitedEndgameClock().orElseThrow().longDiagonalExtension()).isTrue();
    assertThat(state.limitedEndgameClock().orElseThrow().currentLimit()).isEqualTo(21);
    assertThat(game.outcome(state)).isEmpty();
  }

  @Test
  void sixteenMoveEndingDrawsWithoutTheLongDiagonalExtension() {
    var base =
        DraughtsState.position(
            0, DraughtsState.squares(48, 49, 50), 0, DraughtsState.squares(10), Player.WHITE);
    var clock =
        new LimitedEndgameClock(LimitedEndgameKind.SIXTEEN_MOVES, Player.WHITE, 15, 15, false);
    var state = withDrawClocks(base, 15, 15, Optional.of(clock));

    state = game.applyMove(state, DraughtsMove.quiet(48, 43));
    state = game.applyMove(state, DraughtsMove.quiet(10, 4));

    assertThat(state.limitedEndgameClock().orElseThrow().longDiagonalExtension()).isFalse();
    assertThat(game.outcome(state).orElseThrow().reason())
        .isEqualTo(TerminationReason.LIMITED_ENDGAME_MOVE_LIMIT);
  }

  @Test
  void captureDuringASixteenMoveEndingPreservesTheLargerLimit() {
    var base =
        DraughtsState.position(
            0, DraughtsState.squares(23, 48, 49), 0, DraughtsState.squares(5), Player.BLACK);
    var clock =
        new LimitedEndgameClock(LimitedEndgameKind.SIXTEEN_MOVES, Player.WHITE, 3, 3, false);
    var state = withDrawClocks(base, 0, 0, Optional.of(clock));

    var next =
        game.applyMove(
            state, DraughtsMove.capture(5, java.util.List.of(28), java.util.List.of(23)));

    assertThat(next.limitedEndgameClock().orElseThrow().kind())
        .isEqualTo(LimitedEndgameKind.SIXTEEN_MOVES);
    assertThat(next.limitedEndgameClock().orElseThrow().blackMoves()).isEqualTo(4);
    assertThat(next.limitedEndgameClocks())
        .extracting(LimitedEndgameClock::kind)
        .containsExactly(LimitedEndgameKind.SIXTEEN_MOVES, LimitedEndgameKind.FIVE_MOVES);
    assertThat(next.limitedEndgameClocks().get(1).whiteMoves()).isZero();
    assertThat(next.limitedEndgameClocks().get(1).blackMoves()).isZero();
  }

  @Test
  void winOnTheLastAllowedMoveTakesPrecedenceOverADraw() {
    var base =
        DraughtsState.position(
            0, DraughtsState.squares(28), 0, DraughtsState.squares(5), Player.BLACK);
    var clock = new LimitedEndgameClock(LimitedEndgameKind.FIVE_MOVES, Player.WHITE, 5, 4, false);
    var state = withDrawClocks(base, 0, 0, Optional.of(clock));

    var finalState =
        game.applyMove(
            state, DraughtsMove.capture(5, java.util.List.of(32), java.util.List.of(28)));

    assertThat(game.outcome(finalState).orElseThrow().winner()).isEqualTo(Player.BLACK);
    assertThat(game.outcome(finalState).orElseThrow().reason())
        .isEqualTo(TerminationReason.NO_PIECES);
  }

  private static DraughtsState withDrawClocks(
      DraughtsState base,
      int whiteKingMoves,
      int blackKingMoves,
      Optional<LimitedEndgameClock> limitedClock) {
    return new DraughtsState(
        base.whiteMen(),
        base.whiteKings(),
        base.blackMen(),
        base.blackKings(),
        base.playerToMove(),
        base.repetitionHistory(),
        whiteKingMoves,
        blackKingMoves,
        limitedClock.stream().toList(),
        base.ply());
  }

  private static boolean historyContains(
      RepetitionHistory latest, RepetitionHistory expectedReference) {
    var current = Optional.of(latest);
    while (current.isPresent()) {
      if (current.orElseThrow() == expectedReference) {
        return true;
      }
      current = current.orElseThrow().previous();
    }
    return false;
  }
}
