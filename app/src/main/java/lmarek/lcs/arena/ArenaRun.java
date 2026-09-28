package lmarek.lcs.arena;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lmarek.lcs.agent.Agent;
import lmarek.lcs.agent.PlayedTurn;
import lmarek.lcs.draughts.DraughtsGame;
import lmarek.lcs.draughts.DraughtsMove;
import lmarek.lcs.draughts.DraughtsState;
import lmarek.lcs.game.GameExecutionException;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.GameRunner;
import lmarek.lcs.game.Player;
import lmarek.lcs.xcs.MatchingExecutor;
import lmarek.lcs.xcs.XcsInspectable;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** One isolated, memory-only arena run. */
final class ArenaRun implements AutoCloseable {
  private static final long PROGRESS_INTERVAL_NANOS = 250_000_000L;
  private static final int MAXIMUM_GAME_PLIES = 1_000;

  private final String id = UUID.randomUUID().toString();
  private final ArenaRunRequest request;
  private final @Nullable XcsRuleSetStore ruleSets;
  private final FullSpeedGameRunner fullSpeedGameRunner;
  private final MatchingExecutor matchingExecutor;
  private final Instant createdAt = Instant.now();
  private final DraughtsGame game = new DraughtsGame();
  private final GameRunner<DraughtsState, DraughtsMove> runner =
      new GameRunner<>(game, MAXIMUM_GAME_PLIES);
  private final Agent<DraughtsState, DraughtsMove> agentA;
  private final Agent<DraughtsState, DraughtsMove> agentB;
  private final List<CompetitorView> competitors;
  private final ArenaHistory history = new ArenaHistory(createdAt);
  private final ArenaEventStream events = new ArenaEventStream();
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(Thread.ofVirtual().name("arena-" + id).factory());
  private final @Nullable ThreadPoolExecutor fullSpeedPool;
  private final ScheduledExecutorService publicationScheduler =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("arena-publication-" + id).factory());
  private final Object control = new Object();
  private final Object publication = new Object();
  private final SplittableRandom evaluationSeeds;
  private final Map<String, Long> evolutionSequences = new HashMap<>();
  private volatile RunStatus status = RunStatus.RUNNING;
  private volatile Pacing pacing;
  private volatile boolean pauseRequested;
  private volatile boolean stopRequested;
  private volatile @Nullable CurrentGameView currentGame;
  private volatile @Nullable String lastError;
  private volatile @Nullable Thread workerThread;
  private Map<String, Map<String, Double>> capturedTelemetry;
  private final ProgressPublication<Progress> progress;
  private volatile ArenaSnapshot cachedSnapshot;
  private long displayedGames;
  private long revision;
  private long lastProgressPublicationNanos;
  private long lastNamedCheckpointNanos = System.nanoTime();
  private volatile long lastSuccessfulCheckpointMillis;
  private long lastTelemetryNanos = System.nanoTime();
  private double lastLearningIterations;
  private double learningUpdatesPerSecond;
  private @Nullable ScheduledFuture<?> trailingPublication;
  private boolean publicationClosed;

  ArenaRun(ArenaRunRequest request) {
    this(request, ArenaPerformance.defaults(), null);
  }

  ArenaRun(ArenaRunRequest request, ArenaPerformance performance) {
    this(request, performance, null);
  }

  ArenaRun(
      ArenaRunRequest request, ArenaPerformance performance, @Nullable XcsRuleSetStore ruleSets) {
    this(request, performance, ruleSets, null);
  }

  ArenaRun(
      ArenaRunRequest request,
      ArenaPerformance performance,
      @Nullable XcsRuleSetStore ruleSets,
      @Nullable FullSpeedGameRunner fullSpeedGameRunner) {
    this.request = Objects.requireNonNull(request);
    this.ruleSets = ruleSets;
    this.fullSpeedGameRunner =
        fullSpeedGameRunner == null ? this::runFullSpeedGame : fullSpeedGameRunner;
    ArenaConfigurationSchema.validate(request);
    pacing = request.pacing();
    var rootSeeds = new SplittableRandom(request.parsedSeed());
    int fullSpeedWorkers =
        request.trainingWorkers() == null
            ? Runtime.getRuntime().availableProcessors()
            : Math.min(request.trainingWorkers(), Runtime.getRuntime().availableProcessors());
    int matchingWorkers =
        request.trainingMode() == TrainingMode.FULL_SPEED
            ? Math.min(fullSpeedWorkers, 15)
            : performance.matchingWorkers();
    int learningWorkers =
        request.trainingMode() == TrainingMode.FULL_SPEED ? 1 : performance.learningWorkers();
    matchingExecutor =
        new MatchingExecutor(matchingWorkers, learningWorkers, performance.parallelThreshold());
    fullSpeedPool =
        request.trainingMode() == TrainingMode.FULL_SPEED
            ? new ThreadPoolExecutor(
                fullSpeedWorkers,
                fullSpeedWorkers,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(fullSpeedWorkers),
                Thread.ofPlatform().name("arena-game-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy())
            : null;
    System.getLogger(ArenaRun.class.getName())
        .log(
            System.Logger.Level.INFO,
            "Arena {0}: matching workers={1}, learning workers={2}, threshold={3}",
            id,
            matchingWorkers,
            learningWorkers,
            performance.parallelThreshold());
    var factory = new DraughtsAgentFactory(game, matchingExecutor);
    var savedA = loadRuleSet(request.agentA());
    var savedB = loadRuleSet(request.agentB());
    if (request.agentA().ruleSet() != null
        && request.agentA().ruleSet().equals(request.agentB().ruleSet())) {
      throw new IllegalArgumentException("A rule set can be assigned to only one competitor");
    }
    agentA =
        factory.create(
            "A",
            request.agentA(),
            rootSeeds.nextLong(),
            savedA == null ? null : savedA.parameters(),
            savedA == null ? null : savedA.population());
    agentB =
        factory.create(
            "B",
            request.agentB(),
            rootSeeds.nextLong(),
            savedB == null ? null : savedB.parameters(),
            savedB == null ? null : savedB.population());
    if (request.trainingMode() == TrainingMode.FULL_SPEED) {
      enableSampledDeletion(agentA);
      enableSampledDeletion(agentB);
    }
    competitors = List.of(competitorView(agentA), competitorView(agentB));
    evaluationSeeds = rootSeeds.split();
    capturedTelemetry = captureTelemetry();
    progress =
        new ProgressPublication<>(new Progress(currentGame, capturedTelemetry, history.view()));
    cachedSnapshot = buildSnapshot(0);
  }

  void start() {
    executor.execute(this::runLoop);
  }

  boolean isActive() {
    return status == RunStatus.RUNNING
        || status == RunStatus.PAUSING
        || status == RunStatus.PAUSED
        || status == RunStatus.STOPPING;
  }

  ArenaSnapshot snapshot() {
    return cachedSnapshot;
  }

  SseEmitter subscribe() {
    synchronized (publication) {
      var snapshot = cachedSnapshot;
      return events.subscribe(new ArenaUpdate(id, snapshot.revision()));
    }
  }

  void pause() {
    synchronized (control) {
      if (status != RunStatus.RUNNING) {
        throw new ArenaConflictException("The current run is not running");
      }
      pauseRequested = true;
      status = RunStatus.PAUSING;
      control.notifyAll();
    }
    publishNow(false);
  }

  void resume() {
    synchronized (control) {
      if (status != RunStatus.PAUSING && status != RunStatus.PAUSED) {
        throw new ArenaConflictException("The current run is not pausing or paused");
      }
      pauseRequested = false;
      status = RunStatus.RUNNING;
      control.notifyAll();
    }
    publishNow(false);
  }

  void stop() {
    @Nullable Thread worker;
    synchronized (control) {
      requireControllable();
      stopRequested = true;
      pauseRequested = false;
      status = RunStatus.STOPPING;
      worker = workerThread;
      control.notifyAll();
    }
    publishNow(false);
    if (worker != null) {
      worker.interrupt();
    }
  }

  void setPacing(Pacing newPacing) {
    synchronized (control) {
      requireControllable();
      pacing = Objects.requireNonNull(newPacing);
      control.notifyAll();
    }
    publishNow(false);
  }

  GameReplay replay(long gameNumber) {
    if (gameNumber <= 0) {
      throw new IllegalArgumentException("gameNumber must be positive");
    }
    return history.replay(gameNumber);
  }

  RuleView rule(String competitorId, long ruleId) {
    if (ruleId <= 0) {
      throw new IllegalArgumentException("ruleId must be positive");
    }
    var agent = competitor(competitorId);
    if (!(agent instanceof XcsInspectable inspectable)) {
      throw new ArenaNotFoundException("That competitor does not have an XCS population");
    }
    var inspection =
        inspectable
            .inspectRule(ruleId)
            .orElseThrow(() -> new ArenaNotFoundException("No retained rule has that ID"));
    return RuleView.from(inspection.rule(), inspection.changes());
  }

  private Agent<DraughtsState, DraughtsMove> competitor(String competitorId) {
    return switch (competitorId) {
      case "A" -> agentA;
      case "B" -> agentB;
      default -> throw new ArenaNotFoundException("Competitor must be A or B");
    };
  }

  private void runLoop() {
    workerThread = Thread.currentThread();
    try {
      while (!stopRequested) {
        checkpoint();
        if (fullSpeedPool == null) playTrainingGame();
        else playFullSpeedBatch();
        if ((agentA.learns() || agentB.learns())
            && history.trainingGames() % request.evaluationIntervalValue() == 0) {
          playEvaluationSeries();
        }
      }
      finishStopped();
    } catch (StopRequestedException exception) {
      finishStopped();
    } catch (CancellationException exception) {
      if (stopRequested) {
        finishStopped();
      } else {
        finishFailed(exception);
      }
    } catch (RuntimeException exception) {
      finishFailed(exception);
    } finally {
      matchingExecutor.close();
      if (fullSpeedPool != null) {
        fullSpeedPool.shutdownNow();
        awaitFullSpeedWorkers(fullSpeedPool);
      }
      workerThread = null;
      executor.shutdown();
      publicationScheduler.shutdownNow();
    }
  }

  void playFullSpeedBatch() {
    var pool = Objects.requireNonNull(fullSpeedPool);
    long completedBeforeBatch = history.trainingGames();
    int untilEvaluation =
        request.evaluationIntervalValue()
            - (int) (completedBeforeBatch % request.evaluationIntervalValue());
    int count = Math.min(pool.getCorePoolSize(), untilEvaluation);
    var futures = new ArrayList<Future<FullSpeedGame>>();
    for (int index = 0; index < count; index++) {
      long trainingNumber = completedBeforeBatch + index + 1;
      long gameNumber = ++displayedGames;
      futures.add(pool.submit(() -> fullSpeedGameRunner.run(gameNumber, trainingNumber)));
    }
    var results = new ArrayList<FullSpeedGame>();
    try {
      for (var future : futures) results.add(future.get());
    } catch (InterruptedException exception) {
      futures.forEach(future -> future.cancel(true));
      pool.shutdownNow();
      awaitFullSpeedWorkers(pool);
      Thread.currentThread().interrupt();
      throw new CancellationException("Full-speed training batch interrupted");
    } catch (java.util.concurrent.ExecutionException exception) {
      futures.forEach(future -> future.cancel(true));
      pool.shutdownNow();
      awaitFullSpeedWorkers(pool);
      var cause = exception.getCause();
      if (cause instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("Full-speed game worker failed", cause);
    }

    for (var result : results) {
      long trainingNumber = history.trainingGames() + 1;
      var telemetry = captureTelemetry();
      history.recordTrainingSummary(
          result.replay(),
          result.outcome(),
          result.whiteId(),
          result.plies(),
          Objects.requireNonNull(telemetry.get("A")),
          Objects.requireNonNull(telemetry.get("B")));
      capturedTelemetry = telemetry;
      collectEvolution("A", agentA, trainingNumber);
      collectEvolution("B", agentB, trainingNumber);
    }
    long now = System.nanoTime();
    if (now - lastNamedCheckpointNanos >= TimeUnit.SECONDS.toNanos(60)) {
      persistRuleSets();
      lastNamedCheckpointNanos = now;
    }
    currentGame = null;
    publishProgress();
  }

  private static void awaitFullSpeedWorkers(ThreadPoolExecutor pool) {
    boolean interrupted = Thread.interrupted();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    try {
      while (!pool.isTerminated()) {
        try {
          if (!pool.awaitTermination(
              Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
            throw new IllegalStateException(
                "Full-speed game workers did not stop within five seconds");
          }
        } catch (InterruptedException exception) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  private FullSpeedGame runFullSpeedGame(long gameNumber, long trainingNumber) {
    long gameSeed = mixSeed(request.parsedSeed() ^ trainingNumber);
    boolean aIsWhite = trainingNumber % 2 == 1;
    var whiteTemplate = aIsWhite ? agentA : agentB;
    var blackTemplate = aIsWhite ? agentB : agentA;
    var white = episodeAgent(whiteTemplate, aIsWhite ? "A" : "B", mixSeed(gameSeed));
    var black = episodeAgent(blackTemplate, aIsWhite ? "B" : "A", mixSeed(gameSeed + 1));
    var turns = gameNumber % 100 == 0 ? new ArrayList<ReplayTurn>() : null;
    var result =
        runner.run(
            white,
            black,
            gameNumber,
            true,
            turn -> {
              if (turns != null) turns.add(replayTurn(turn, white, black));
            });
    GameReplay replay = null;
    if (turns != null) {
      var winner = winnerIdentity(result.outcome(), white.id());
      replay =
          new GameReplay(
              gameNumber,
              false,
              white.id(),
              black.id(),
              BoardView.from(result.initialState()),
              turns,
              winner == null ? "Draw" : winner + " win",
              result.outcome().reason().name());
    }
    return new FullSpeedGame(result.outcome(), white.id(), result.turns().size(), replay);
  }

  @SuppressWarnings("unchecked")
  private Agent<DraughtsState, DraughtsMove> episodeAgent(
      Agent<DraughtsState, DraughtsMove> template, String id, long seed) {
    if (template instanceof lmarek.lcs.xcs.XcsAgent<?, ?> xcs) {
      return ((lmarek.lcs.xcs.XcsAgent<DraughtsState, DraughtsMove>) xcs).sharedEpisode(seed);
    }
    var configuration = id.equals("A") ? request.agentA() : request.agentB();
    return new DraughtsAgentFactory(game, matchingExecutor).create(id, configuration, seed);
  }

  private static long mixSeed(long value) {
    value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
    value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
    return value ^ (value >>> 31);
  }

  private static void enableSampledDeletion(Agent<?, ?> agent) {
    if (agent instanceof lmarek.lcs.xcs.XcsAgent<?, ?> xcs) xcs.sampledDeletion(true);
  }

  private void finishStopped() {
    try {
      persistRuleSets();
    } catch (RuntimeException exception) {
      finishFailed(exception);
      return;
    }
    refreshLearningProgress(history.trainingGames() + 1);
    captureProgress();
    status = RunStatus.STOPPED;
    publishNow(true);
  }

  private void finishFailed(RuntimeException exception) {
    refreshLearningProgress(history.trainingGames() + 1);
    lastError = failureMessage(exception);
    captureProgress();
    status = RunStatus.ERROR;
    publishNow(true);
  }

  private void playTrainingGame() {
    long trainingNumber = history.trainingGames() + 1;
    boolean aIsWhite = trainingNumber % 2 == 1;
    var completed = playGame(aIsWhite ? agentA : agentB, aIsWhite ? agentB : agentA, false, true);
    var telemetry = captureTelemetry();
    history.recordTraining(
        completed.replay(),
        completed.outcome(),
        Objects.requireNonNull(telemetry.get("A")),
        Objects.requireNonNull(telemetry.get("B")));
    persistRuleSets();
    capturedTelemetry = telemetry;
    collectEvolution("A", agentA, trainingNumber);
    collectEvolution("B", agentB, trainingNumber);
    publishProgress();
  }

  private XcsRuleSetStore.@Nullable SavedRuleSet loadRuleSet(AgentConfiguration configuration) {
    if (configuration.ruleSet() == null) return null;
    if (ruleSets == null) {
      throw new IllegalStateException("XCS rule-set persistence is unavailable");
    }
    return ruleSets.load(configuration.ruleSet());
  }

  private void persistRuleSets() {
    if (ruleSets == null) return;
    persistRuleSet(request.agentA(), agentA);
    persistRuleSet(request.agentB(), agentB);
    lastSuccessfulCheckpointMillis = System.currentTimeMillis();
  }

  private void persistRuleSet(
      AgentConfiguration configuration, Agent<DraughtsState, DraughtsMove> agent) {
    if (configuration.ruleSet() == null) return;
    if (!(agent instanceof lmarek.lcs.xcs.XcsAgent<?, ?> xcs)) {
      throw new IllegalStateException("Named rule sets require an XCS agent");
    }
    Objects.requireNonNull(ruleSets)
        .save(configuration.ruleSet(), xcs.parameters(), xcs.populationSnapshot());
  }

  private void playEvaluationSeries() {
    var frozenA = agentA.frozenCopy(evaluationSeeds.nextLong());
    var frozenB = agentB.frozenCopy(evaluationSeeds.nextLong());
    double aScore = 0.0;
    double bScore = 0.0;
    for (int index = 0; index < request.evaluationGamesValue(); index++) {
      checkpoint();
      boolean aIsWhite = index % 2 == 0;
      var completed =
          playGame(aIsWhite ? frozenA : frozenB, aIsWhite ? frozenB : frozenA, true, false);
      history.recordEvaluationReplay(completed.replay());
      var winner = winnerIdentity(completed.outcome(), completed.replay().whiteCompetitorId());
      if (winner == null) {
        aScore += 0.5;
        bScore += 0.5;
      } else if (winner.equals("A")) {
        aScore += 1.0;
      } else {
        bScore += 1.0;
      }
      publishProgress();
    }
    history.recordEvaluationSeries(history.trainingGames(), aScore, bScore);
    publishProgress();
  }

  private CompletedGame playGame(
      Agent<DraughtsState, DraughtsMove> white,
      Agent<DraughtsState, DraughtsMove> black,
      boolean evaluation,
      boolean training) {
    displayedGames++;
    long gameNumber = displayedGames;
    var replayTurns = new ArrayList<ReplayTurn>();
    currentGame =
        new CurrentGameView(
            gameNumber,
            evaluation,
            white.id(),
            black.id(),
            white.displayName(),
            black.displayName(),
            white.kind().name(),
            black.kind().name(),
            0,
            BoardView.from(game.initialState()),
            null,
            null,
            null);
    publishGameProgress(evaluation);

    var result =
        runner.run(
            white,
            black,
            gameNumber,
            training,
            turn -> {
              var replayTurn = replayTurn(turn, white, black);
              replayTurns.add(replayTurn);
              currentGame =
                  new CurrentGameView(
                      gameNumber,
                      evaluation,
                      white.id(),
                      black.id(),
                      white.displayName(),
                      black.displayName(),
                      white.kind().name(),
                      black.kind().name(),
                      replayTurn.ply(),
                      replayTurn.boardAfter(),
                      replayTurn.move(),
                      replayTurn.trace(),
                      replayTurn.agentKind());
              if (training) {
                refreshLearningProgress(history.trainingGames() + 1);
              }
              publishGameProgress(evaluation);
              if (game.outcome(turn.transition().after()).isEmpty()) {
                checkpoint();
                awaitPacing(evaluation ? 0 : pacing.delayMillis());
              }
            });
    var winner = winnerIdentity(result.outcome(), white.id());
    var replay =
        new GameReplay(
            gameNumber,
            evaluation,
            white.id(),
            black.id(),
            BoardView.from(result.initialState()),
            replayTurns,
            winner == null ? "Draw" : winner + " win",
            result.outcome().reason().name());
    return new CompletedGame(replay, result.outcome());
  }

  private void publishGameProgress(boolean evaluation) {
    if (!evaluation && pacing == Pacing.LIVE) {
      captureProgress();
      publishNow(false);
    } else {
      publishProgress();
    }
  }

  private static ReplayTurn replayTurn(
      PlayedTurn<DraughtsState, DraughtsMove> turn,
      Agent<DraughtsState, DraughtsMove> white,
      Agent<DraughtsState, DraughtsMove> black) {
    var actorAgent = turn.transition().actor() == Player.WHITE ? white : black;
    var move = MoveView.from(turn.transition().move());
    return new ReplayTurn(
        turn.transition().ply() + 1,
        turn.transition().actor(),
        move,
        BoardView.from(turn.transition().after()),
        DecisionTraceView.from(turn.decision().trace(), move.actionId()),
        actorAgent.kind().name());
  }

  // Called only by the game worker, outside the publication lock.
  private void refreshLearningProgress(long trainingNumber) {
    capturedTelemetry = captureTelemetry();
    collectEvolution("A", agentA, trainingNumber);
    collectEvolution("B", agentB, trainingNumber);
  }

  private void collectEvolution(
      String competitorId, Agent<DraughtsState, DraughtsMove> agent, long trainingNumber) {
    if (!(agent instanceof XcsInspectable inspectable)) {
      return;
    }
    long sequence = evolutionSequences.getOrDefault(competitorId, 0L);
    var newEvents = inspectable.evolutionEventsAfter(sequence, 2_000);
    for (var event : newEvents) {
      history.addEvolution(
          new EvolutionHighlight(
              trainingNumber, competitorId, event.ruleId(), event.type().name(), event.detail()));
      sequence = event.sequence();
    }
    evolutionSequences.put(competitorId, sequence);
  }

  private void checkpoint() {
    synchronized (control) {
      if (stopRequested) {
        throw new StopRequestedException();
      }
      if (pauseRequested && status == RunStatus.PAUSING) {
        if (request.trainingMode() == TrainingMode.FULL_SPEED) persistRuleSets();
        status = RunStatus.PAUSED;
        publishNow(false);
      }
      while (pauseRequested && !stopRequested) {
        try {
          control.wait();
        } catch (InterruptedException exception) {
          if (stopRequested) {
            throw new StopRequestedException();
          }
          Thread.currentThread().interrupt();
          throw new CancellationException("Arena worker interrupted while paused");
        }
      }
      if (stopRequested) {
        throw new StopRequestedException();
      }
    }
  }

  private void awaitPacing(long delayMillis) {
    if (delayMillis <= 0) {
      return;
    }
    synchronized (control) {
      long deadline = System.nanoTime() + delayMillis * 1_000_000L;
      long remainingNanos = deadline - System.nanoTime();
      while (remainingNanos > 0 && !stopRequested && !pauseRequested && pacing == Pacing.LIVE) {
        try {
          control.wait(Math.max(1, remainingNanos / 1_000_000L));
        } catch (InterruptedException exception) {
          if (stopRequested) {
            throw new StopRequestedException();
          }
          Thread.currentThread().interrupt();
          throw new CancellationException("Arena worker interrupted during pacing");
        }
        remainingNanos = deadline - System.nanoTime();
      }
    }
    checkpoint();
  }

  private void captureProgress() {
    progress.publish(new Progress(currentGame, capturedTelemetry, history.view()));
  }

  private void publishProgress() {
    captureProgress();
    synchronized (publication) {
      if (publicationClosed) {
        return;
      }
      long now = System.nanoTime();
      long elapsed = now - lastProgressPublicationNanos;
      if (lastProgressPublicationNanos == 0 || elapsed >= PROGRESS_INTERVAL_NANOS) {
        cancelTrailingPublication();
        lastProgressPublicationNanos = now;
        publishLocked(false);
        return;
      }
      if (trailingPublication == null) {
        long delay = PROGRESS_INTERVAL_NANOS - elapsed;
        trailingPublication =
            publicationScheduler.schedule(this::publishTrailing, delay, TimeUnit.NANOSECONDS);
      }
    }
  }

  private void publishTrailing() {
    synchronized (publication) {
      trailingPublication = null;
      if (publicationClosed) {
        return;
      }
      lastProgressPublicationNanos = System.nanoTime();
      publishLocked(false);
    }
  }

  private void publishNow(boolean terminal) {
    synchronized (publication) {
      if (publicationClosed) {
        return;
      }
      cancelTrailingPublication();
      publishLocked(terminal);
    }
  }

  private void publishLocked(boolean terminal) {
    revision++;
    cachedSnapshot = buildSnapshot(revision);
    events.publish(new ArenaUpdate(id, revision));
    if (terminal) {
      publicationClosed = true;
      events.close();
    }
  }

  private void cancelTrailingPublication() {
    if (trailingPublication != null) {
      trailingPublication.cancel(false);
      trailingPublication = null;
    }
  }

  private ArenaSnapshot buildSnapshot(long snapshotRevision) {
    var captured = progress.get();
    var historyView = captured.history();
    return new ArenaSnapshot(
        id,
        snapshotRevision,
        status,
        pacing,
        createdAt,
        request.seed(),
        competitors,
        historyView.trainingGames(),
        historyView.evaluationGames(),
        historyView.statistics(),
        captured.currentGame(),
        historyView.replaySummaries(),
        historyView.evolutionHighlights(),
        captured.telemetry(),
        historyView.charts(),
        historyView.historyRevisions(),
        request.trainingMode(),
        fullSpeedPool == null ? 1 : fullSpeedPool.getCorePoolSize(),
        lastError);
  }

  private Map<String, Map<String, Double>> captureTelemetry() {
    var metricsA = Map.copyOf(agentA.telemetry().metrics());
    var metricsB = Map.copyOf(agentB.telemetry().metrics());
    double iterations =
        metricsA.getOrDefault("xcs.iteration", 0.0) + metricsB.getOrDefault("xcs.iteration", 0.0);
    long now = System.nanoTime();
    double elapsed = Math.max(0.001, (now - lastTelemetryNanos) / 1_000_000_000.0);
    learningUpdatesPerSecond = Math.max(0.0, iterations - lastLearningIterations) / elapsed;
    lastTelemetryNanos = now;
    lastLearningIterations = iterations;
    var runtime = Runtime.getRuntime();
    double cpu =
        java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean processBean
            ? Math.max(0.0, processBean.getProcessCpuLoad())
            : 0.0;
    return Map.of(
        "A", metricsA,
        "B", metricsB,
        "run",
            Map.of(
                "training.workers",
                    (double) (fullSpeedPool == null ? 1 : fullSpeedPool.getCorePoolSize()),
                "training.updatesPerSecond", learningUpdatesPerSecond,
                "process.cpu", cpu,
                "heap.used", (double) (runtime.totalMemory() - runtime.freeMemory()),
                "checkpoint.epochMillis", (double) lastSuccessfulCheckpointMillis));
  }

  private static CompetitorView competitorView(Agent<?, ?> agent) {
    return new CompetitorView(
        agent.id(),
        agent.displayName(),
        agent.kind().name(),
        agent.learns(),
        agent instanceof XcsInspectable);
  }

  private void requireControllable() {
    if (status != RunStatus.RUNNING && status != RunStatus.PAUSING && status != RunStatus.PAUSED) {
      throw new ArenaConflictException("The current run is not active");
    }
  }

  private static @Nullable String winnerIdentity(GameOutcome outcome, String whiteCompetitorId) {
    if (outcome.isDraw()) {
      return null;
    }
    return outcome.winner() == Player.WHITE
        ? whiteCompetitorId
        : ("A".equals(whiteCompetitorId) ? "B" : "A");
  }

  private static String failureMessage(RuntimeException exception) {
    var message = exception.getMessage();
    if (exception instanceof GameExecutionException) {
      return message == null ? "Game execution failed" : message;
    }
    return "%s: %s"
        .formatted(exception.getClass().getSimpleName(), message == null ? "no detail" : message);
  }

  @Override
  public void close() {
    @Nullable Thread worker;
    synchronized (control) {
      stopRequested = true;
      pauseRequested = false;
      worker = workerThread;
      control.notifyAll();
    }
    if (worker != null) {
      worker.interrupt();
    }
    executor.shutdownNow();
    publicationScheduler.shutdownNow();
    boolean interrupted = Thread.interrupted();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    try {
      while (!executor.isTerminated()) {
        try {
          if (!executor.awaitTermination(
              Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
            throw new ArenaConflictException(
                "Previous arena computation did not terminate within five seconds; replacement refused");
          }
        } catch (InterruptedException exception) {
          interrupted = true;
        }
      }
      matchingExecutor.close();
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
    synchronized (publication) {
      publicationClosed = true;
      cancelTrailingPublication();
      events.close();
    }
  }

  private record Progress(
      @Nullable CurrentGameView currentGame,
      Map<String, Map<String, Double>> telemetry,
      ArenaHistoryView history) {}

  private record CompletedGame(GameReplay replay, GameOutcome outcome) {}

  record FullSpeedGame(
      GameOutcome outcome, String whiteId, int plies, @Nullable GameReplay replay) {}

  @FunctionalInterface
  interface FullSpeedGameRunner {
    FullSpeedGame run(long gameNumber, long trainingNumber);
  }

  private static final class StopRequestedException extends RuntimeException {}
}
