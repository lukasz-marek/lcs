package lmarek.lcs.arena;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/arena")
public final class ArenaController {
  private final ArenaService arena;

  public ArenaController(ArenaService arena) {
    this.arena = arena;
  }

  @GetMapping("/options")
  ArenaOptions options() {
    return arena.options();
  }

  @GetMapping("/rule-sets")
  java.util.List<XcsRuleSetStore.RuleSetInfo> ruleSets() {
    return arena.ruleSets();
  }

  @PostMapping("/runs")
  ResponseEntity<ArenaSnapshot> start(@RequestBody ArenaRunRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(arena.start(request));
  }

  @GetMapping("/runs/current")
  ArenaSnapshot current() {
    return arena.snapshot();
  }

  @GetMapping("/runs/current/live")
  ArenaLiveSnapshot live() {
    return ArenaLiveSnapshot.from(arena.snapshot());
  }

  @GetMapping("/runs/current/history")
  ArenaHistoryResponse history(
      @RequestParam(defaultValue = "") String runId,
      @RequestParam(defaultValue = "-1") long charts,
      @RequestParam(defaultValue = "-1") long replays,
      @RequestParam(defaultValue = "-1") long evolution) {
    return ArenaHistoryResponse.from(arena.snapshot(), runId, charts, replays, evolution);
  }

  @PostMapping("/runs/current/pause")
  ArenaSnapshot pause() {
    return arena.pause();
  }

  @PostMapping("/runs/current/resume")
  ArenaSnapshot resume() {
    return arena.resume();
  }

  @PostMapping("/runs/current/stop")
  ArenaSnapshot stop() {
    return arena.stop();
  }

  @PatchMapping("/runs/current/pacing")
  ArenaSnapshot pacing(@RequestBody PacingRequest request) {
    return arena.pacing(request.pacing());
  }

  @GetMapping(path = "/runs/current/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  SseEmitter events() {
    return arena.events();
  }

  @GetMapping("/runs/current/replays/{gameNumber}")
  GameReplay replay(@PathVariable long gameNumber) {
    return arena.replay(gameNumber);
  }

  @GetMapping("/runs/current/competitors/{competitorId}/rules/{ruleId}")
  RuleView rule(@PathVariable String competitorId, @PathVariable long ruleId) {
    return arena.rule(competitorId, ruleId);
  }

  @ExceptionHandler(ArenaConflictException.class)
  ResponseEntity<Map<String, String>> conflict(ArenaConflictException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(ArenaNotFoundException.class)
  ResponseEntity<Map<String, String>> notFound(ArenaNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
    var status = exception.getStatusCode();
    return ResponseEntity.status(status)
        .body(
            Map.of(
                "error", exception.getReason() == null ? "Request failed" : exception.getReason()));
  }

  @ExceptionHandler({IllegalArgumentException.class})
  ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
  }
}
