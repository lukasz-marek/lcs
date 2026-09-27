package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.EpisodeContext;
import lmarek.lcs.agent.EpisodeResult;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.TerminationReason;
import org.junit.jupiter.api.Test;

class XcsAgentEquivalenceTest {
  private static final StateActionEncoder<String, String> ENCODER =
      new StateActionEncoder<>() {
        @Override
        public CategoricalState encode(String state, Player perspective) {
          return new CategoricalState(state, perspective.name());
        }

        @Override
        public String actionId(String move, Player perspective) {
          return move;
        }
      };

  @Test
  void optimizedAndReferenceAgentsKeepSeededTracesAndLearningOrder() {
    var p = XcsParameters.defaults();
    var expected = new ReferenceXcsAgent<String, String>("x", "x", ENCODER, p, 834);
    var actual = new XcsAgent<String, String>("x", "x", ENCODER, p, 834);
    expected.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    actual.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    for (int ply = 0; ply < 80; ply++) {
      var context =
          new DecisionContext<>(
              "state" + ply % 4, Player.WHITE, List.of("a", "b", "c"), ply, true, 1);
      assertThat(actual.decide(context)).isEqualTo(expected.decide(context));
      assertThat(actual.rules()).isEqualTo(expected.rules());
      assertThat(actual.evolutionEventsAfter(0, 2_000))
          .isEqualTo(expected.evolutionEventsAfter(0, 2_000));
    }
    var episode =
        new EpisodeResult<String, String>(
            "start",
            "end",
            GameOutcome.win(Player.WHITE, TerminationReason.NO_PIECES),
            List.of(),
            Player.WHITE,
            true,
            1);
    actual.endEpisode(episode);
    expected.endEpisode(episode);
    assertThat(actual.rules()).isEqualTo(expected.rules());
    assertThat(actual.ruleChanges(actual.rules().getFirst().id()))
        .isEqualTo(expected.ruleChanges(expected.rules().getFirst().id()));
  }
}
