package lmarek.lcs.arena;

import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public final class ArenaService {
  private @Nullable ArenaRun current;
  private boolean replacing;
  private final ArenaPerformance performance;

  public ArenaService(
      @Value("${arena.performance.matching-workers:1}") int workers,
      @Value("${arena.performance.parallel-threshold:32768}") int threshold) {
    performance = new ArenaPerformance(workers, threshold);
  }

  public ArenaOptions options() {
    var options = ArenaConfigurationSchema.options();
    return new ArenaOptions(
        options.agentKinds(),
        options.pacingModes(),
        options.defaultSeed(),
        options.evaluationInterval(),
        options.evaluationGames(),
        performance);
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
      var run = new ArenaRun(request, performance);
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
