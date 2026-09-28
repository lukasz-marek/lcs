package lmarek.lcs.arena;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public final class ArenaService {
  private @Nullable ArenaRun current;
  private boolean replacing;
  private final ArenaPerformance performance;
  private final @Nullable XcsRuleSetStore ruleSets;

  @Autowired
  public ArenaService(
      @Value("${arena.performance.matching-workers:1}") int workers,
      @Value("${arena.performance.learning-workers:16}") int learningWorkers,
      @Value("${arena.performance.parallel-threshold:32768}") int threshold,
      XcsRuleSetStore ruleSets) {
    performance = new ArenaPerformance(workers, learningWorkers, threshold);
    this.ruleSets = ruleSets;
  }

  ArenaService(int workers, int learningWorkers, int threshold) {
    performance = new ArenaPerformance(workers, learningWorkers, threshold);
    this.ruleSets = null;
  }

  public ArenaOptions options() {
    var options = ArenaConfigurationSchema.options();
    return new ArenaOptions(
        options.agentKinds(),
        options.pacingModes(),
        options.defaultSeed(),
        options.evaluationInterval(),
        options.evaluationGames(),
        performance,
        options.trainingModes(),
        options.availableTrainingWorkers());
  }

  public ArenaSnapshot start(ArenaRunRequest request) {
    ArenaConfigurationSchema.validate(request);
    ArenaRun previous;
    synchronized (this) {
      if (replacing || (current != null && current.isActive())) {
        throw new ArenaConflictException("An arena run is already active or being replaced");
      }
      replacing = true;
      previous = current;
    }
    try {
      if (previous != null) previous.close();
      var run = new ArenaRun(request, performance, ruleSets);
      synchronized (this) {
        current = run;
      }
      var initial = run.snapshot();
      run.start();
      return initial;
    } finally {
      synchronized (this) {
        replacing = false;
      }
    }
  }

  public java.util.List<XcsRuleSetStore.RuleSetInfo> ruleSets() {
    return ruleSets == null ? java.util.List.of() : ruleSets.list();
  }

  public ArenaSnapshot snapshot() {
    return current().snapshot();
  }

  public ArenaSnapshot pause() {
    var run = current();
    run.pause();
    return run.snapshot();
  }

  public ArenaSnapshot resume() {
    var run = current();
    run.resume();
    return run.snapshot();
  }

  public ArenaSnapshot stop() {
    var run = current();
    run.stop();
    return run.snapshot();
  }

  public ArenaSnapshot pacing(Pacing pacing) {
    var run = current();
    run.setPacing(pacing);
    return run.snapshot();
  }

  public SseEmitter events() {
    return current().subscribe();
  }

  public GameReplay replay(long gameNumber) {
    return current().replay(gameNumber);
  }

  public RuleView rule(String competitorId, long ruleId) {
    return current().rule(competitorId, ruleId);
  }

  private synchronized ArenaRun current() {
    if (current == null) {
      throw new ArenaNotFoundException("No arena run has been started");
    }
    return current;
  }

  @PreDestroy
  void close() {
    ArenaRun run;
    synchronized (this) {
      run = current;
    }
    if (run != null) run.close();
  }
}
