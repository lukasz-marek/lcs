package lmarek.lcs.draughts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PositionAnalysisTest {
  @Test
  void checkedAndAnalyzedPathsAgreeAndRejectOtherGames() {
    var game = new DraughtsGame();
    var analysis = game.analyze(game.initialState());
    assertThat(analysis.outcome()).isEqualTo(game.outcome(analysis.state()));
    assertThat(analysis.legalMoves()).isEqualTo(game.legalMoves(analysis.state()));
    for (var move : analysis.legalMoves()) {
      assertThat(game.applyAnalyzedMove(analysis, move))
          .isEqualTo(game.applyMove(analysis.state(), move));
      assertThatThrownBy(() -> new DraughtsGame().applyAnalyzedMove(analysis, move))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> analysis.legalMoves().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> game.applyAnalyzedMove(analysis, DraughtsMove.quiet(1, 6)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
