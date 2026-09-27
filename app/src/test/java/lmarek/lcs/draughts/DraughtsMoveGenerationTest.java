package lmarek.lcs.draughts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lmarek.lcs.game.Player;
import org.junit.jupiter.api.Test;

class DraughtsMoveGenerationTest {
  private final DraughtsGame game = new DraughtsGame();

  @Test
  void createsTheStandardInitialPositionAndNineOpeningMoves() {
    var state = game.initialState();

    assertThat(Long.bitCount(state.whiteMen())).isEqualTo(20);
    assertThat(Long.bitCount(state.blackMen())).isEqualTo(20);
    assertThat(state.whiteKings() | state.blackKings()).isZero();
    assertThat(state.playerToMove()).isEqualTo(Player.WHITE);
    assertThat(game.legalMoves(state)).hasSize(9).allMatch(move -> !move.isCapture());
  }

  @Test
  void matchesPublishedFullPathPerftThroughDepthSix() {
    var expected = new long[] {1, 9, 81, 658, 4_265, 27_117, 167_140};

    for (var depth = 1; depth <= 6; depth++) {
      assertThat(perft(game.initialState(), depth))
          .as("depth %s", depth)
          .isEqualTo(expected[depth]);
    }
  }

  @Test
  void menMoveForwardButCaptureInBothDirections() {
    var state = position(new int[] {28}, new int[] {}, new int[] {33}, new int[] {}, Player.WHITE);

    assertThat(game.legalMoves(state))
        .containsExactly(DraughtsMove.capture(28, List.of(39), List.of(33)));
  }

  @Test
  void mandatoryCaptureKeepsOnlyPathsWithTheGlobalMaximumPieceCount() {
    var state =
        position(
            new int[] {32, 46}, new int[] {}, new int[] {19, 28, 41}, new int[] {}, Player.WHITE);

    assertThat(game.legalMoves(state))
        .containsExactly(DraughtsMove.capture(32, List.of(23, 14), List.of(28, 19)));
  }

  @Test
  void keepsEveryCapturePathTiedForTheMaximum() {
    var state =
        position(new int[] {32}, new int[] {}, new int[] {27, 28}, new int[] {}, Player.WHITE);

    assertThat(game.legalMoves(state))
        .containsExactlyInAnyOrder(
            DraughtsMove.capture(32, List.of(21), List.of(27)),
            DraughtsMove.capture(32, List.of(23), List.of(28)));
  }

  @Test
  void flyingKingsCanLandAnywhereBeyondTheCapturedPiece() {
    var state = position(new int[] {}, new int[] {46}, new int[] {37}, new int[] {}, Player.WHITE);

    assertThat(game.legalMoves(state))
        .containsExactlyInAnyOrder(
            DraughtsMove.capture(46, List.of(32), List.of(37)),
            DraughtsMove.capture(46, List.of(28), List.of(37)),
            DraughtsMove.capture(46, List.of(23), List.of(37)),
            DraughtsMove.capture(46, List.of(19), List.of(37)),
            DraughtsMove.capture(46, List.of(14), List.of(37)),
            DraughtsMove.capture(46, List.of(10), List.of(37)),
            DraughtsMove.capture(46, List.of(5), List.of(37)));
  }

  @Test
  void flyingKingsHaveLongRangeQuietMoves() {
    var state = position(new int[] {}, new int[] {28}, new int[] {1}, new int[] {}, Player.WHITE);

    assertThat(game.legalMoves(state))
        .hasSize(17)
        .contains(
            DraughtsMove.quiet(28, 5), DraughtsMove.quiet(28, 46), DraughtsMove.quiet(28, 50));
  }

  @Test
  void capturedPiecesRemainBlockingUntilTheCaptureSequenceEnds() {
    var state =
        position(new int[] {}, new int[] {28}, new int[] {23, 32}, new int[] {}, Player.WHITE);

    assertThat(game.legalMoves(state))
        .isNotEmpty()
        .allMatch(move -> move.capturedSquares().size() == 1);
    assertThat(game.legalMoves(state).stream().map(DraughtsMove::capturedSquares))
        .contains(List.of(23), List.of(32));
  }

  @Test
  void aKingMayReturnToAnEarlierEmptyLandingSquare() {
    var state =
        position(
            new int[] {}, new int[] {23}, new int[] {19, 20, 29, 30}, new int[] {}, Player.WHITE);
    var loop = DraughtsMove.capture(23, List.of(14, 25, 34, 23), List.of(19, 20, 30, 29));

    assertThat(game.legalMoves(state)).contains(loop);
    assertThat(game.applyMove(state, loop).whiteKings()).isEqualTo(DraughtsState.squares(23));
  }

  @Test
  void aManPromotesOnlyWhenTheCompleteCaptureEndsOnTheBackRank() {
    var crossesBackRank =
        position(new int[] {13}, new int[] {}, new int[] {7, 8}, new int[] {}, Player.WHITE);
    var roundTrip = DraughtsMove.capture(13, List.of(2, 11), List.of(8, 7));

    var afterRoundTrip = game.applyMove(crossesBackRank, roundTrip);
    assertThat(afterRoundTrip.whiteMen()).isEqualTo(DraughtsState.squares(11));
    assertThat(afterRoundTrip.whiteKings()).isZero();

    var endsOnBackRank =
        position(new int[] {13}, new int[] {}, new int[] {8}, new int[] {}, Player.WHITE);
    var promoted = game.applyMove(endsOnBackRank, DraughtsMove.capture(13, List.of(2), List.of(8)));
    assertThat(promoted.whiteMen()).isZero();
    assertThat(promoted.whiteKings()).isEqualTo(DraughtsState.squares(2));
  }

  @Test
  void applyMoveRemovesEveryCaptureAndRejectsAnIllegalPartialPath() {
    var state =
        position(new int[] {32}, new int[] {}, new int[] {19, 28}, new int[] {}, Player.WHITE);
    var complete = DraughtsMove.capture(32, List.of(23, 14), List.of(28, 19));

    var next = game.applyMove(state, complete);
    assertThat(next.whiteMen()).isEqualTo(DraughtsState.squares(14));
    assertThat(next.blackMen()).isZero();
    assertThat(next.playerToMove()).isEqualTo(Player.BLACK);
    assertThatThrownBy(
            () -> game.applyMove(state, DraughtsMove.capture(32, List.of(23), List.of(28))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvesNativeAndActorRelativeActionIdsOnlyThroughTheLegalMap() {
    var state = position(new int[] {46}, new int[] {}, new int[] {1}, new int[] {}, Player.BLACK);
    var nativeMove = DraughtsMove.quiet(1, 6);

    assertThat(game.findLegalMove(state, nativeMove.actionId())).contains(nativeMove);
    assertThat(game.findActorRelativeLegalMove(state, "50-45")).contains(nativeMove);
    assertThat(game.findActorRelativeLegalMove(state, "50-43")).isEmpty();
  }

  private static DraughtsState position(
      int[] whiteMen, int[] whiteKings, int[] blackMen, int[] blackKings, Player player) {
    return DraughtsState.position(
        DraughtsState.squares(whiteMen),
        DraughtsState.squares(whiteKings),
        DraughtsState.squares(blackMen),
        DraughtsState.squares(blackKings),
        player);
  }

  private long perft(DraughtsState state, int depth) {
    if (depth == 0) {
      return 1;
    }
    long nodes = 0;
    for (var move : game.legalMoves(state)) {
      nodes += perft(game.applyMove(state, move), depth - 1);
    }
    return nodes;
  }
}
