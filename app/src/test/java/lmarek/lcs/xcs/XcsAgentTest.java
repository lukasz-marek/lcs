package lmarek.lcs.xcs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.EpisodeContext;
import lmarek.lcs.agent.EpisodeResult;
import lmarek.lcs.agent.XcsDecisionTrace;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;
import lmarek.lcs.game.TerminationReason;
import org.junit.jupiter.api.Test;

class XcsAgentTest {
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
  void coversEveryUnrepresentedLegalActionAndKeepsStableRuleIds() {
    var agent = agent(defaultsWith(100, 0.0, 25), 42);
    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));

    var decision = agent.decide(context("position", List.of("a", "b", "c"), 1));
    var trace = (XcsDecisionTrace) decision.trace();

    assertThat(agent.rules()).hasSize(3);
    assertThat(agent.rules())
        .extracting(XcsRuleSnapshot::actionId)
        .containsExactlyInAnyOrder("a", "b", "c");
    assertThat(agent.rules()).extracting(XcsRuleSnapshot::id).doesNotHaveDuplicates();
    assertThat(trace.unknownLegalActions()).isEmpty();
    assertThat(trace.predictions()).containsOnlyKeys("a", "b", "c");
    assertThat(trace.matchingRuleIds()).hasSize(3);
  }

  @Test
  void updatesErrorFromOldPredictionBeforeUpdatingPrediction() {
    var agent = agent(defaultsWith(100, 0.0, 25), 3);
    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    agent.decide(context("position", List.of("only"), 1));

    agent.endEpisode(result(1, Player.WHITE));

    var learned = agent.rules().getFirst();
    assertThat(learned.prediction()).isEqualTo(1.0);
    assertThat(learned.predictionError()).isEqualTo(1.0);
    assertThat(learned.experience()).isEqualTo(1);
  }

  @Test
  void delaysBootstrappedUpdateUntilTheSamePlayerActsAgain() {
    var agent = agent(defaultsWith(100, 0.0, 25), 4);
    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    agent.decide(context("next", List.of("valuable"), 1));
    agent.endEpisode(result(1, Player.WHITE));

    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 2));
    agent.decide(context("previous", List.of("waiting"), 2));
    var waitingBeforeNextTurn = ruleForAction(agent, "waiting");
    assertThat(waitingBeforeNextTurn.experience()).isZero();

    agent.decide(context("next", List.of("valuable"), 2));

    var waitingAfterNextTurn = ruleForAction(agent, "waiting");
    assertThat(waitingAfterNextTurn.experience()).isEqualTo(1);
    assertThat(waitingAfterNextTurn.prediction()).isEqualTo(XcsParameters.defaults().gamma());
  }

  @Test
  void excludesClassifiersWhoseActionsAreNotCurrentlyLegal() {
    var agent = agent(defaultsWith(100, 0.0, 25), 5);
    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    agent.decide(context("same", List.of("legal", "now-illegal"), 1));
    agent.endEpisode(result(1, Player.WHITE));
    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 2));

    var trace = (XcsDecisionTrace) agent.decide(context("same", List.of("legal"), 2)).trace();

    assertThat(trace.matchingRuleIds())
        .allSatisfy(id -> assertThat(agent.rule(id).orElseThrow().actionId()).isEqualTo("legal"));
  }

  @Test
  void frozenCopyExploitsWithoutCoveringOrChangingRulesAndMarksUnknownActions() {
    var learning = agent(defaultsWith(100, 1.0, 25), 7);
    learning.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    learning.decide(context("known", List.of("known-action"), 1));
    learning.endEpisode(result(1, Player.WHITE));
    var frozen = learning.frozenCopy(99);
    var before = frozen.rules();
    frozen.beginEpisode(new EpisodeContext<>("start", Player.WHITE, false, 2));

    var decision = frozen.decide(context("unseen", List.of("new-action"), 2, false));
    frozen.endEpisode(result(2, Player.BLACK, false));
    var trace = (XcsDecisionTrace) decision.trace();

    assertThat(decision.move()).isEqualTo("new-action");
    assertThat(trace.exploratory()).isFalse();
    assertThat(trace.predictions()).containsEntry("new-action", 0.0);
    assertThat(trace.unknownLegalActions()).containsExactly("new-action");
    assertThat(frozen.rules()).isEqualTo(before);
    assertThat(frozen.learns()).isFalse();
  }

  @Test
  void raisesClearCapacityErrorBeforePartiallyCoveringLegalActions() {
    var agent = agent(defaultsWith(1, 0.0, 25), 1);
    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));

    assertThatThrownBy(() -> agent.decide(context("position", List.of("a", "b"), 1)))
        .isInstanceOf(XcsCapacityException.class)
        .hasMessageContaining("cannot safely cover 2 legal action");
    assertThat(agent.rules()).isEmpty();
  }

  @Test
  void geneticAlgorithmCreatesNewIdsAndRecordsParentsAndMutationCandidates() {
    var agent = agent(defaultsWith(100, 0.0, 0, 1.0), 11);
    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    agent.decide(context("position", List.of("a", "b"), 1));
    agent.decide(context("position", List.of("a", "b"), 1));

    var offspring =
        agent.evolutionEventsAfter(0, 100).stream()
            .filter(event -> event.type() == XcsEvolutionEvent.Type.OFFSPRING)
            .toList();
    assertThat(offspring).hasSize(2);
    assertThat(offspring).allSatisfy(event -> assertThat(event.parentIds()).isNotEmpty());
    assertThat(agent.evolutionEventsAfter(0, 100))
        .anySatisfy(event -> assertThat(event.type()).isEqualTo(XcsEvolutionEvent.Type.MUTATED));
  }

  @Test
  void identicalSeedsProduceIdenticalDecisionsAndPopulations() {
    var first = agent(defaultsWith(100, 0.5, 0), 1234);
    var second = agent(defaultsWith(100, 0.5, 0), 1234);
    first.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    second.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));

    for (var ply = 0; ply < 8; ply++) {
      var context = context("state-" + (ply % 2), List.of("a", "b", "c"), 1);
      assertThat(first.decide(context).move()).isEqualTo(second.decide(context).move());
    }

    assertThat(first.rules()).isEqualTo(second.rules());
    assertThat(first.evolutionEventsAfter(0, 1_000))
        .isEqualTo(second.evolutionEventsAfter(0, 1_000));
  }

  @Test
  void retainsExactlyTheLastFiftyMetricChangesForEachLiveRule() {
    var agent = agent(defaultsWith(100, 0.0, Integer.MAX_VALUE), 8);
    long ruleId = 0;
    for (var episode = 1; episode <= 60; episode++) {
      agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, episode));
      agent.decide(context("same", List.of("only"), episode));
      if (ruleId == 0) {
        ruleId = agent.rules().getFirst().id();
      }
      agent.endEpisode(result(episode, Player.WHITE));
    }

    var changes = agent.ruleChanges(ruleId);

    assertThat(changes).hasSize(50);
    assertThat(changes)
        .allSatisfy(
            change -> {
              assertThat(change.type()).isEqualTo(XcsEvolutionEvent.Type.UPDATED);
              assertThat(change.detail())
                  .contains("prediction", "error", "fitness", "numerosity", "action-set size");
            });
    assertThat(changes)
        .extracting(XcsEvolutionEvent::sequence)
        .isSortedAccordingTo(Long::compareTo);
  }

  @Test
  void returnsRuleAndChangesFromOneInspection() {
    var agent = agent(defaultsWith(100, 0.0, Integer.MAX_VALUE), 18);
    agent.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    agent.decide(context("same", List.of("only"), 1));
    agent.endEpisode(result(1, Player.WHITE));
    long ruleId = agent.rules().getFirst().id();

    var inspection = agent.inspectRule(ruleId).orElseThrow();

    assertThat(inspection.rule()).isEqualTo(agent.rule(ruleId).orElseThrow());
    assertThat(inspection.changes()).isEqualTo(agent.ruleChanges(ruleId));
  }

  @Test
  void frozenPopulationRemainsIsolatedWhenTheSourceLearnsLater() {
    var source = agent(defaultsWith(100, 0.0, Integer.MAX_VALUE), 21);
    source.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 1));
    source.decide(context("known", List.of("only"), 1));
    source.endEpisode(result(1, Player.WHITE));
    var frozen = source.frozenCopy(22);
    var frozenBefore = frozen.rules();

    source.beginEpisode(new EpisodeContext<>("start", Player.WHITE, true, 2));
    source.decide(context("new", List.of("new-action"), 2));
    source.endEpisode(result(2, Player.WHITE));

    assertThat(frozen.rules()).isEqualTo(frozenBefore);
    assertThat(source.rules()).isNotEqualTo(frozen.rules());
  }

  @Test
  void learnsToPreferARewardedActionAndFreezesThatPreference() {
    var learner = agent(defaultsWith(100, 0.5, Integer.MAX_VALUE), 731);
    for (int episode = 1; episode <= 200; episode++) {
      learner.beginEpisode(new EpisodeContext<>("choice", Player.WHITE, true, episode));
      var decision = learner.decide(context("choice", List.of("win", "lose"), episode));
      learner.endEpisode(
          result(episode, decision.move().equals("win") ? Player.WHITE : Player.BLACK));
    }
    assertThat(ruleForAction(learner, "win").prediction()).isEqualTo(1.0);
    assertThat(ruleForAction(learner, "lose").prediction()).isEqualTo(-1.0);
    var frozen = learner.frozenCopy(91);
    var rulesBefore = frozen.rules();
    for (int episode = 201; episode <= 220; episode++) {
      frozen.beginEpisode(new EpisodeContext<>("choice", Player.WHITE, false, episode));
      assertThat(frozen.decide(context("choice", List.of("win", "lose"), episode, false)).move())
          .isEqualTo("win");
      frozen.endEpisode(result(episode, Player.WHITE, false));
    }
    assertThat(frozen.rules()).isEqualTo(rulesBefore);
  }

  private static XcsAgent<String, String> agent(XcsParameters parameters, long seed) {
    return new XcsAgent<>("xcs", "XCS", ENCODER, parameters, seed);
  }

  private static XcsRuleSnapshot ruleForAction(XcsAgent<String, String> agent, String action) {
    return agent.rules().stream()
        .filter(rule -> rule.actionId().equals(action))
        .findFirst()
        .orElseThrow();
  }

  private static DecisionContext<String, String> context(
      String state, List<String> moves, long episode) {
    return context(state, moves, episode, true);
  }

  private static DecisionContext<String, String> context(
      String state, List<String> moves, long episode, boolean training) {
    return new DecisionContext<>(state, Player.WHITE, moves, 0, training, episode);
  }

  private static EpisodeResult<String, String> result(long episode, Player winner) {
    return result(episode, winner, true);
  }

  private static EpisodeResult<String, String> result(
      long episode, Player winner, boolean training) {
    return new EpisodeResult<>(
        "start",
        "end",
        GameOutcome.win(winner, TerminationReason.NO_PIECES),
        List.of(),
        Player.WHITE,
        training,
        episode);
  }

  private static XcsParameters defaultsWith(int population, double exploration, int gaThreshold) {
    return defaultsWith(population, exploration, gaThreshold, 0.04);
  }

  private static XcsParameters defaultsWith(
      int population, double exploration, int gaThreshold, double mutation) {
    var defaults = XcsParameters.defaults();
    return new XcsParameters(
        population,
        defaults.beta(),
        defaults.gamma(),
        exploration,
        defaults.accuracyAlpha(),
        defaults.errorThreshold(),
        defaults.accuracyPower(),
        0.0,
        defaults.initialPrediction(),
        defaults.initialPredictionError(),
        defaults.initialFitness(),
        gaThreshold,
        defaults.crossoverProbability(),
        mutation,
        defaults.deletionExperienceThreshold(),
        defaults.deletionFitnessFraction(),
        defaults.subsumptionExperienceThreshold(),
        defaults.gaSubsumption());
  }
}
