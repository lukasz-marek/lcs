package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.EpisodeContext;
import lmarek.lcs.agent.EpisodeResult;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.TerminationReason;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @org.junit.jupiter.api.Test
  @org.junit.jupiter.api.Timeout(15)
  void inspectionRemainsCoherentDuringParallelLearning() throws Exception {
    try (var executor = new MatchingExecutor(2, 4, 1)) {
      var agent =
          new XcsAgent<String, String>("x", "x", ENCODER, XcsParameters.defaults(), 834, executor);
      agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
      agent.decide(new DecisionContext<>("s", Player.WHITE, List.of("a", "b"), 0, true, 1));
      var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
      var inspector =
          Thread.ofPlatform()
              .start(
                  () -> {
                    try {
                      for (int i = 0; i < 500; i++) {
                        for (var rule : agent.rules()) {
                          agent
                              .inspectRule(rule.id())
                              .ifPresent(
                                  view -> {
                                    assertThat(view.rule().id()).isEqualTo(rule.id());
                                    assertThat(view.changes()).hasSizeLessThanOrEqualTo(50);
                                    assertThat(
                                            view.changes().stream()
                                                .map(XcsEvolutionEvent::sequence)
                                                .toList())
                                        .isSorted();
                                  });
                        }
                      }
                    } catch (Throwable problem) {
                      failure.set(problem);
                    }
                  });
      for (int i = 1; i < 300; i++) {
        agent.decide(new DecisionContext<>("s", Player.WHITE, List.of("a", "b"), i, true, 1));
      }
      inspector.join();
      assertThat(failure.get()).isNull();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 4, 8, 15, 16})
  void optimizedAndReferenceAgentsKeepSeededTracesAndLearningOrder(int workers) {
    try (var executor = new MatchingExecutor(Math.min(workers, 15), workers, 1)) {
      var p = XcsParameters.defaults();
      var expected = new ReferenceXcsAgent<String, String>("x", "x", ENCODER, p, 834);
      var actual = new XcsAgent<String, String>("x", "x", ENCODER, p, 834, executor);
      expected.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
      actual.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
      for (int ply = 0; ply < 80; ply++) {
        var context =
            new DecisionContext<>(
                "state" + ply % 4, Player.WHITE, List.of("a", "b", "c"), ply, true, 1);
        assertThat(actual.decide(context)).isEqualTo(expected.decide(context));
        assertThat(actual.rules()).isEqualTo(expected.rules());
        for (var rule : actual.rules()) {
          assertThat(actual.ruleChanges(rule.id())).isEqualTo(expected.ruleChanges(rule.id()));
        }
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
}
