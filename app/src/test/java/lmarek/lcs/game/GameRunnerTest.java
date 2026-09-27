package lmarek.lcs.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lmarek.lcs.agent.Agent;
import lmarek.lcs.agent.AgentDecision;
import lmarek.lcs.agent.AgentKind;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.agent.EpisodeContext;
import lmarek.lcs.agent.EpisodeResult;
import lmarek.lcs.agent.MoveTransition;
import lmarek.lcs.agent.RandomDecisionTrace;
import org.junit.jupiter.api.Test;

class GameRunnerTest {
  @Test
  void completesLifecycleWhenTheLastPermittedPlyWins() {
    var white = new RecordingAgent("white", "finish");
    var black = new RecordingAgent("black", "unused");
    var observed = new ArrayList<String>();

    var result =
        new GameRunner<>(new OneMoveGame(), 1)
            .run(white, black, 7, true, turn -> observed.add(turn.transition().move()));

    assertThat(result.outcome().winner()).isEqualTo(Player.WHITE);
    assertThat(result.turns()).hasSize(1);
    assertThat(observed).containsExactly("finish");
    assertThat(white.events).containsExactly("begin-WHITE", "observe-finish", "end-1.0");
    assertThat(black.events).containsExactly("begin-BLACK", "observe-finish", "end--1.0");
  }

  @Test
  void rejectsAnIllegalAgentDecision() {
    var white = new RecordingAgent("white", "invented");

    assertThatThrownBy(
            () ->
                new GameRunner<>(new OneMoveGame(), 10)
                    .run(
                        white, new RecordingAgent("black", "unused"), 1, true, GameObserver.none()))
        .isInstanceOf(GameExecutionException.class)
        .hasMessageContaining("illegal move");
  }

  @Test
  void aSafetyAbortIsNotConvertedIntoARewardedDraw() {
    var endless =
        new TurnBasedGame<Integer, String>() {
          @Override
          public Integer initialState() {
            return 0;
          }

          @Override
          public Player playerToMove(Integer state) {
            return state % 2 == 0 ? Player.WHITE : Player.BLACK;
          }

          @Override
          public List<String> legalMoves(Integer state) {
            return List.of("next");
          }

          @Override
          public Integer applyMove(Integer state, String move) {
            return state + 1;
          }

          @Override
          public Optional<GameOutcome> outcome(Integer state) {
            return Optional.empty();
          }
        };
    var white = new RecordingAgent("white", "next");
    var black = new RecordingAgent("black", "next");

    assertThatThrownBy(
            () -> new GameRunner<>(endless, 3).run(white, black, 1, true, GameObserver.none()))
        .isInstanceOf(GameExecutionException.class)
        .hasMessageContaining("safety limit");
    assertThat(white.events).noneMatch(event -> event.startsWith("end-"));
    assertThat(black.events).noneMatch(event -> event.startsWith("end-"));
    assertThat(white.events).contains("abort");
    assertThat(black.events).contains("abort");
  }

  @Test
  void observationFailureStillNotifiesEveryRecipientAndAbortsBothAgents() {
    var white = new RecordingAgent("white", "finish", "observe");
    var black = new RecordingAgent("black", "unused", "observe");
    var observed = new ArrayList<String>();

    assertThatThrownBy(
            () ->
                new GameRunner<>(new OneMoveGame(), 1)
                    .run(white, black, 1, true, turn -> observed.add(turn.transition().move())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("white-observe")
        .satisfies(
            failure ->
                assertThat(failure.getSuppressed())
                    .extracting(Throwable::getMessage)
                    .containsExactly("black-observe"));
    assertThat(observed).containsExactly("finish");
    assertThat(white.events).contains("observe-finish", "abort");
    assertThat(black.events).contains("observe-finish", "abort");
  }

  @Test
  void terminalFailureStillEndsTheOtherAgentBeforeAbortingBoth() {
    var white = new RecordingAgent("white", "finish", "end");
    var black = new RecordingAgent("black", "unused");

    assertThatThrownBy(
            () ->
                new GameRunner<>(new OneMoveGame(), 1)
                    .run(white, black, 1, true, GameObserver.none()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("white-end");
    assertThat(black.events).contains("end--1.0");
    assertThat(white.events).endsWith("abort");
    assertThat(black.events).endsWith("abort");
  }

  private static final class OneMoveGame implements TurnBasedGame<Integer, String> {
    @Override
    public Integer initialState() {
      return 0;
    }

    @Override
    public Player playerToMove(Integer state) {
      return Player.WHITE;
    }

    @Override
    public List<String> legalMoves(Integer state) {
      return state == 0 ? List.of("finish") : List.of();
    }

    @Override
    public Integer applyMove(Integer state, String move) {
      return 1;
    }

    @Override
    public Optional<GameOutcome> outcome(Integer state) {
      return state == 1
          ? Optional.of(GameOutcome.win(Player.WHITE, TerminationReason.NO_PIECES))
          : Optional.empty();
    }
  }

  private static final class RecordingAgent implements Agent<Integer, String> {
    private final String id;
    private final String move;
    private final String failingCallback;
    private final List<String> events = new ArrayList<>();

    private RecordingAgent(String id, String move) {
      this(id, move, "");
    }

    private RecordingAgent(String id, String move, String failingCallback) {
      this.id = id;
      this.move = move;
      this.failingCallback = failingCallback;
    }

    @Override
    public String id() {
      return id;
    }

    @Override
    public String displayName() {
      return id;
    }

    @Override
    public AgentKind kind() {
      return AgentKind.RANDOM;
    }

    @Override
    public boolean learns() {
      return false;
    }

    @Override
    public void beginEpisode(EpisodeContext<Integer> context) {
      events.add("begin-" + context.seat());
      fail("begin");
    }

    @Override
    public AgentDecision<String> decide(DecisionContext<Integer, String> context) {
      return new AgentDecision<>(move, new RandomDecisionTrace(context.legalMoves().size()));
    }

    @Override
    public void observe(MoveTransition<Integer, String> transition) {
      events.add("observe-" + transition.move());
      fail("observe");
    }

    @Override
    public void endEpisode(EpisodeResult<Integer, String> result) {
      events.add("end-" + result.reward());
      fail("end");
    }

    @Override
    public void abortEpisode() {
      events.add("abort");
      fail("abort");
    }

    @Override
    public Agent<Integer, String> frozenCopy(long seed) {
      return new RecordingAgent(id, move);
    }

    private void fail(String callback) {
      if (failingCallback.equals(callback)) {
        throw new IllegalStateException(id + "-" + callback);
      }
    }
  }
}
