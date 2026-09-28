package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.EpisodeContext;
import lmarek.lcs.agent.EpisodeResult;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.TerminationReason;
import org.junit.jupiter.api.Test;

class XcsSharedEpisodeConcurrencyTest {
  private static final int EPISODES = 8;
  private static final int ROUNDS = 20;
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
  void concurrentEpisodesShareLearningAndReleaseEveryPendingRuleOnEndAndAbort() throws Exception {
    var template = new XcsAgent<String, String>("x", "XCS", ENCODER, parameters(), 71);
    var episodes =
        java.util.stream.IntStream.range(0, EPISODES)
            .mapToObj(index -> template.sharedEpisode(100 + index))
            .toList();
    var seats =
        java.util.stream.IntStream.range(0, EPISODES)
            .mapToObj(index -> index % 2 == 0 ? Player.WHITE : Player.BLACK)
            .toList();

    try (var workers = Executors.newFixedThreadPool(EPISODES)) {
      for (int round = 1; round <= ROUNDS; round++) {
        int episodeNumber = round;
        var allDecided = new CountDownLatch(EPISODES);
        var continueEpisode = new CountDownLatch(1);
        var completed = new ArrayList<java.util.concurrent.Future<?>>();
        for (int index = 0; index < EPISODES; index++) {
          var episode = episodes.get(index);
          var seat = seats.get(index);
          completed.add(
              workers.submit(
                  () -> {
                    episode.beginEpisode(new EpisodeContext<>("start", seat, true, episodeNumber));
                    episode.decide(
                        new DecisionContext<>(
                            "same-position", seat, List.of("only-action"), 0, true, episodeNumber));
                    allDecided.countDown();
                    if (!continueEpisode.await(5, TimeUnit.SECONDS)) {
                      throw new IllegalStateException("Episode barrier timed out");
                    }
                    if (episodeNumber % 2 == 0) {
                      episode.endEpisode(result(episodeNumber, seat));
                    } else {
                      episode.abortEpisode();
                    }
                    return null;
                  }));
        }

        assertThat(allDecided.await(5, TimeUnit.SECONDS))
            .as("all episodes reach decision barrier")
            .isTrue();
        assertThat(template.protectedRuleReferenceCount()).isEqualTo(EPISODES);
        assertThat(template.rules()).hasSize(2);
        var saved = template.populationSnapshot();
        var restored =
            XcsAgent.restore(
                "x", "XCS", ENCODER, parameters(), 72, MatchingExecutor.sequential(), saved);
        assertThat(restored.rules()).isEqualTo(template.rules());
        restored.verifyPopulationIndexes();
        continueEpisode.countDown();
        for (var future : completed) future.get(5, TimeUnit.SECONDS);
        assertThat(template.protectedRuleReferenceCount()).isZero();
        template.verifyPopulationIndexes();
      }
    }

    assertThat(template.rules()).hasSize(2);
    assertThat(template.rules()).extracting(XcsRuleSnapshot::experience).containsExactly(40L, 40L);
  }

  private static XcsParameters parameters() {
    var defaults = XcsParameters.defaults();
    return new XcsParameters(
        100,
        defaults.beta(),
        defaults.gamma(),
        0.0,
        defaults.accuracyAlpha(),
        defaults.errorThreshold(),
        defaults.accuracyPower(),
        0.0,
        defaults.initialPrediction(),
        defaults.initialPredictionError(),
        defaults.initialFitness(),
        Integer.MAX_VALUE,
        defaults.crossoverProbability(),
        defaults.mutationProbability(),
        defaults.deletionExperienceThreshold(),
        defaults.deletionFitnessFraction(),
        defaults.subsumptionExperienceThreshold(),
        defaults.gaSubsumption());
  }

  private static EpisodeResult<String, String> result(long episode, Player seat) {
    return new EpisodeResult<>(
        "start",
        "end",
        GameOutcome.win(seat, TerminationReason.NO_PIECES),
        List.of(),
        seat,
        true,
        episode);
  }
}
