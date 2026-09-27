package lmarek.lcs.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.draughts.DraughtsGame;
import lmarek.lcs.draughts.DraughtsMove;
import lmarek.lcs.draughts.DraughtsState;
import org.junit.jupiter.api.Test;

class MctsReferenceTest {
  @Test
  void analysisPreservesCompleteSeededTrace() {
    var game = new DraughtsGame();
    var state = game.initialState();
    var random = new SplittableRandom(781);
    for (int position = 0; position < 12; position++) {
      if (game.outcome(state).isPresent()) break;
      var moves = game.legalMoves(state);
      var config = new MctsConfig(30, 30, MctsConfig.UCT_EXPLORATION);
      var reference =
          new ReferenceMctsAgent<DraughtsState, DraughtsMove>("a", "a", game, config, position);
      var actual = new MctsAgent<DraughtsState, DraughtsMove>("a", "a", game, config, position);
      var context = new DecisionContext<>(state, state.playerToMove(), moves, position, false, 1);
      assertThat(actual.decide(context)).isEqualTo(reference.decide(context));
      state = game.applyMove(state, moves.get(random.nextInt(moves.size())));
    }
  }
}
