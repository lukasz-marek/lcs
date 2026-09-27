package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lmarek.lcs.draughts.DraughtsGame;
import lmarek.lcs.draughts.DraughtsMove;
import lmarek.lcs.draughts.DraughtsState;
import lmarek.lcs.draughts.LimitedEndgameClock;
import lmarek.lcs.draughts.LimitedEndgameKind;
import lmarek.lcs.draughts.PositionSignature;
import lmarek.lcs.draughts.RepetitionHistory;
import lmarek.lcs.game.Player;
import org.junit.jupiter.api.Test;

class DraughtsXcsEncoderTest {
  private final DraughtsXcsEncoder encoder = new DraughtsXcsEncoder();

  @Test
  void rotatesBlackSoEquivalentPositionsHaveTheSameObservation() {
    var whiteView =
        DraughtsState.position(
            DraughtsState.squares(31),
            DraughtsState.squares(45),
            DraughtsState.squares(20),
            DraughtsState.squares(6),
            Player.WHITE);
    var blackView =
        DraughtsState.position(
            DraughtsState.squares(31),
            DraughtsState.squares(45),
            DraughtsState.squares(20),
            DraughtsState.squares(6),
            Player.BLACK);

    assertThat(encoder.encode(whiteView, Player.WHITE))
        .isEqualTo(encoder.encode(blackView, Player.BLACK));
  }

  @Test
  void retainsTheFullCapturePathAndRotatesEverySquareInTheAction() {
    var whiteMove = DraughtsMove.capture(31, List.of(22, 13), List.of(27, 18));
    var blackMove = DraughtsMove.capture(20, List.of(29, 38), List.of(24, 33));

    assertThat(encoder.actionId(whiteMove, Player.WHITE)).isEqualTo("31x22x13[27,18]");
    assertThat(encoder.actionId(blackMove, Player.BLACK))
        .isEqualTo(encoder.actionId(whiteMove, Player.WHITE));
  }

  @Test
  void resolvesActionsOnlyThroughTheCurrentLegalMoveMap() {
    var game = new DraughtsGame();
    var state = game.initialState();
    var move = game.legalMoves(state).getFirst();
    var action = encoder.actionId(move, Player.WHITE);

    assertThat(encoder.resolveLegal(game, state, Player.WHITE, action)).contains(move);
    assertThat(encoder.resolveLegal(game, state, Player.WHITE, "31-1")).isEmpty();
  }

  @Test
  void includesFiftyBoardCategoriesAndExactDrawContext() {
    var encoded = encoder.encode(DraughtsState.initial(), Player.WHITE);

    assertThat(encoded.values()).hasSize(64);
    assertThat(encoded.values().subList(0, 50))
        .allMatch(
            value ->
                List.of("EMPTY", "SELF_MAN", "SELF_KING", "OPPONENT_MAN", "OPPONENT_KING")
                    .contains(value));
    assertThat(encoded.values().get(51)).startsWith("REPETITION_HISTORY[");
    assertThat(encoded.values()).contains("SELF_KING_ONLY_MOVES_0");
    assertThat(encoded.values()).contains("OPPONENT_KING_ONLY_MOVES_0");
    assertThat(encoded.values()).contains("LIMITED_FIVE_MOVES_ACTIVE_FALSE");
    assertThat(encoded.values()).contains("LIMITED_SIXTEEN_MOVES_ACTIVE_FALSE");
  }

  @Test
  void distinguishesRepetitionWindowsThatHaveTheSameCurrentOccurrenceCount() {
    var current =
        new PositionSignature(
            0, DraughtsState.squares(46), 0, DraughtsState.squares(1), Player.WHITE);
    var priorOne =
        new PositionSignature(
            0, DraughtsState.squares(41), 0, DraughtsState.squares(1), Player.BLACK);
    var priorTwo =
        new PositionSignature(
            0, DraughtsState.squares(42), 0, DraughtsState.squares(1), Player.BLACK);
    var first = stateWithHistory(current, RepetitionHistory.start(priorOne).append(current));
    var second = stateWithHistory(current, RepetitionHistory.start(priorTwo).append(current));

    assertThat(first.currentRepetitionCount()).isEqualTo(second.currentRepetitionCount());
    assertThat(encoder.encode(first, Player.WHITE).values().get(51))
        .isNotEqualTo(encoder.encode(second, Player.WHITE).values().get(51));
  }

  @Test
  void encodesKingAgainstKingWithNoArbitraryStrongerSide() {
    var whiteView =
        DraughtsState.position(
            0, DraughtsState.squares(46), 0, DraughtsState.squares(5), Player.WHITE);
    var blackView =
        DraughtsState.position(
            0, DraughtsState.squares(46), 0, DraughtsState.squares(5), Player.BLACK);

    assertThat(encoder.encode(whiteView, Player.WHITE))
        .isEqualTo(encoder.encode(blackView, Player.BLACK));
    assertThat(encoder.encode(whiteView, Player.WHITE).values())
        .contains("LIMITED_FIVE_MOVES_STRONGER_EQUAL");
  }

  @Test
  void encodesBothSimultaneouslyActiveEndgameClocksActorRelatively() {
    var base =
        DraughtsState.position(
            0, DraughtsState.squares(48, 49), 0, DraughtsState.squares(5), Player.BLACK);
    var state =
        new DraughtsState(
            base.whiteMen(),
            base.whiteKings(),
            base.blackMen(),
            base.blackKings(),
            base.playerToMove(),
            base.repetitionHistory(),
            3,
            4,
            List.of(
                new LimitedEndgameClock(LimitedEndgameKind.SIXTEEN_MOVES, Player.WHITE, 3, 4, true),
                new LimitedEndgameClock(LimitedEndgameKind.FIVE_MOVES, Player.BLACK, 1, 2, false)),
            base.ply());

    var encoded = encoder.encode(state, Player.BLACK);

    assertThat(encoded.values()).contains("LIMITED_SIXTEEN_MOVES_ACTIVE_TRUE");
    assertThat(encoded.values()).contains("LIMITED_SIXTEEN_MOVES_STRONGER_OPPONENT");
    assertThat(encoded.values()).contains("LIMITED_SIXTEEN_MOVES_SELF_MOVES_4");
    assertThat(encoded.values()).contains("LIMITED_SIXTEEN_MOVES_OPPONENT_MOVES_3");
    assertThat(encoded.values()).contains("LIMITED_SIXTEEN_MOVES_EXTENSION_true");
    assertThat(encoded.values()).contains("LIMITED_FIVE_MOVES_ACTIVE_TRUE");
    assertThat(encoded.values()).contains("LIMITED_FIVE_MOVES_STRONGER_SELF");
    assertThat(encoded.values()).contains("LIMITED_FIVE_MOVES_SELF_MOVES_2");
    assertThat(encoded.values()).contains("LIMITED_FIVE_MOVES_OPPONENT_MOVES_1");
  }

  private static DraughtsState stateWithHistory(
      PositionSignature current, RepetitionHistory history) {
    return new DraughtsState(
        current.whiteMen(),
        current.whiteKings(),
        current.blackMen(),
        current.blackKings(),
        current.playerToMove(),
        history,
        0,
        0,
        List.of(),
        history.size() - 1L);
  }
}
