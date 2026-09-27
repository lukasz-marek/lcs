package lmarek.lcs.draughts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lmarek.lcs.game.Player;
import org.junit.jupiter.api.Test;

class DraughtsBoardAndMoveTest {
  @Test
  void mapsEveryPlayableCoordinateAndRotatesSquareNumbers() {
    for (var square = 1; square <= 50; square++) {
      var row = DraughtsBoard.rowOf(square);
      var column = DraughtsBoard.columnOf(square);
      assertThat(DraughtsBoard.squareAt(row, column)).hasValue(square);
      assertThat(DraughtsBoard.rotate(DraughtsBoard.rotate(square))).isEqualTo(square);
    }
    assertThat(DraughtsBoard.squareAt(0, 0)).isEmpty();
    assertThat(DraughtsBoard.squareAt(0, 1)).hasValue(1);
    assertThat(DraughtsBoard.squareAt(9, 8)).hasValue(50);
  }

  @Test
  void actionIdsRoundTripTheEntireCapturePath() {
    var move = DraughtsMove.capture(23, List.of(14, 25, 34, 23), List.of(19, 20, 30, 29));

    assertThat(move.actionId()).isEqualTo("23x14x25x34x23[19,20,30,29]");
    assertThat(DraughtsMove.parseActionId(move.actionId())).isEqualTo(move);
    assertThat(DraughtsMove.parseActionId("31-26")).isEqualTo(DraughtsMove.quiet(31, 26));
  }

  @Test
  void blackOrientationIsAStableHalfTurn() {
    var move = DraughtsMove.capture(6, List.of(17, 28), List.of(11, 22));

    assertThat(move.orientedFor(Player.BLACK).actionId()).isEqualTo("45x34x23[40,29]");
    assertThat(move.orientedFor(Player.BLACK).orientedFor(Player.BLACK)).isEqualTo(move);
    assertThat(move.orientedFor(Player.WHITE)).isSameAs(move);
  }

  @Test
  void rejectsMalformedActions() {
    assertThatThrownBy(() -> DraughtsMove.parseActionId("12x23"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> DraughtsMove.quiet(0, 1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void repetitionHistoriesHaveIterativeStructuralValueSemantics() {
    var initialOne = DraughtsState.initial();
    var initialTwo = DraughtsState.initial();
    var alternate =
        new PositionSignature(
            initialOne.whiteMen(),
            initialOne.whiteKings(),
            initialOne.blackMen(),
            initialOne.blackKings(),
            Player.BLACK);

    assertThat(initialOne).isEqualTo(initialTwo);
    assertThat(initialOne.hashCode()).isEqualTo(initialTwo.hashCode());
    assertThat(initialOne.repetitionHistory().append(alternate))
        .isEqualTo(initialTwo.repetitionHistory().append(alternate));
    assertThat(
            RepetitionHistory.start(alternate).append(initialOne.repetitionHistory().signature()))
        .isNotEqualTo(initialOne.repetitionHistory());
  }
}
