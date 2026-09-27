package lmarek.lcs.search;

import java.util.concurrent.TimeUnit;
import lmarek.lcs.agent.DecisionContext;
import lmarek.lcs.draughts.DraughtsGame;
import lmarek.lcs.draughts.DraughtsMove;
import lmarek.lcs.draughts.DraughtsState;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GameBenchmark {
  private final DraughtsGame game = new DraughtsGame();
  private final DraughtsState state = game.initialState();
  private final DecisionContext<DraughtsState, DraughtsMove> context =
      new DecisionContext<>(state, state.playerToMove(), game.legalMoves(state), 0, false, 1);
  private final MctsAgent<DraughtsState, DraughtsMove> actual =
      new MctsAgent<>("a", "a", game, new MctsConfig(30, 30, MctsConfig.UCT_EXPLORATION), 1);
  private final ReferenceMctsAgent<DraughtsState, DraughtsMove> reference =
      new ReferenceMctsAgent<>(
          "a", "a", game, new MctsConfig(30, 30, MctsConfig.UCT_EXPLORATION), 1);

  @Benchmark
  public Object analysis() {
    return game.analyze(state);
  }

  @Benchmark
  public Object checkedMove() {
    game.outcome(state);
    return game.applyMove(state, game.legalMoves(state).getFirst());
  }

  @Benchmark
  public Object analyzedMove() {
    var analysis = game.analyze(state);
    return game.applyAnalyzedMove(analysis, analysis.legalMoves().getFirst());
  }

  @Benchmark
  public Object mcts() {
    return actual.decide(context);
  }

  @Benchmark
  public Object referenceMcts() {
    return reference.decide(context);
  }
}
