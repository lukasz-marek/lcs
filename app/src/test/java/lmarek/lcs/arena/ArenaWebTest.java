package lmarek.lcs.arena;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArenaWebTest {
  @LocalServerPort private int port;

  @Test
  void servesTheSpectatorAndArenaLifecycle() throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var home = send(client, "GET", "/", null);
      assertThat(home.statusCode()).isEqualTo(200);
      assertThat(home.body()).contains("Crown &amp; Code");

      var options = send(client, "GET", "/api/arena/options", null);
      assertThat(options.statusCode()).isEqualTo(200);
      assertThat(options.body())
          .contains(
              "XCS",
              "MCTS",
              "RANDOM",
              "\"matchingWorkers\":1",
              "\"learningWorkers\":16",
              "\"parallelThreshold\":32768");

      var beforeRun = send(client, "GET", "/api/arena/runs/current", null);
      assertThat(beforeRun.statusCode()).isEqualTo(404);

      var request =
          """
          {
            "agentA":{"kind":"RANDOM","preset":"UNIFORM","settings":{}},
            "agentB":{"kind":"RANDOM","preset":"UNIFORM","settings":{}},
            "seed":"9223372036854775807","pacing":"LIVE","evaluationInterval":500,"evaluationGames":20
          }
          """;
      for (var invalidSeed :
          new String[] {"77", "1.5", "true", "null", "\"01\"", "\"9223372036854775808\""}) {
        var invalidRequest = request.replace("\"9223372036854775807\"", invalidSeed);
        assertThat(send(client, "POST", "/api/arena/runs", invalidRequest).statusCode())
            .as("reject seed %s", invalidSeed)
            .isEqualTo(400);
      }
      var started = send(client, "POST", "/api/arena/runs", request);
      assertThat(started.statusCode()).isEqualTo(201);
      assertThat(started.body()).contains("RUNNING", "\"seed\":\"9223372036854775807\"");
      assertThat(send(client, "POST", "/api/arena/runs", request).statusCode()).isEqualTo(409);

      var mapper = new tools.jackson.databind.ObjectMapper();
      var full = mapper.readTree(send(client, "GET", "/api/arena/runs/current", null).body());
      var live = mapper.readTree(send(client, "GET", "/api/arena/runs/current/live", null).body());
      assertThat(live.has("charts")).isFalse();
      assertThat(live.has("recentReplays")).isFalse();
      assertThat(live.has("evolutionHighlights")).isFalse();
      assertThat(live.has("historyRevisions")).isTrue();
      var historyPath = "/api/arena/runs/current/history?runId=" + full.get("runId").asText();
      var history = mapper.readTree(send(client, "GET", historyPath, null).body());
      assertThat(history.has("charts")).isTrue();
      assertThat(history.has("recentReplays")).isTrue();
      assertThat(history.has("evolutionHighlights")).isTrue();
      var versions = history.get("historyRevisions");
      var unchangedPath =
          historyPath
              + "&charts="
              + versions.get("charts").asLong()
              + "&replays="
              + versions.get("replays").asLong()
              + "&evolution="
              + versions.get("evolution").asLong();
      var unchanged = mapper.readTree(send(client, "GET", unchangedPath, null).body());
      assertThat(unchanged.has("charts")).isFalse();
      assertThat(unchanged.has("recentReplays")).isFalse();
      assertThat(unchanged.has("evolutionHighlights")).isFalse();
      var replaced =
          mapper.readTree(
              send(
                      client,
                      "GET",
                      unchangedPath.replace(full.get("runId").asText(), "old-run"),
                      null)
                  .body());
      assertThat(replaced.has("charts")).isTrue();
      assertThat(replaced.has("recentReplays")).isTrue();

      var events =
          client.send(
              HttpRequest.newBuilder(uri("/api/arena/runs/current/events"))
                  .timeout(Duration.ofSeconds(5))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofInputStream());
      assertThat(events.statusCode()).isEqualTo(200);
      assertThat(events.headers().firstValue("content-type").orElse(""))
          .contains("text/event-stream");
      events.body().close();

      assertThat(send(client, "POST", "/api/arena/runs/current/pause", null).body())
          .containsAnyOf("PAUSING", "PAUSED");
      assertThat(send(client, "POST", "/api/arena/runs/current/resume", null).body())
          .contains("RUNNING");
      assertThat(
              send(client, "PATCH", "/api/arena/runs/current/pacing", "{\"pacing\":\"TURBO\"}")
                  .body())
          .contains("TURBO");
      assertThat(send(client, "POST", "/api/arena/runs/current/stop", null).body())
          .containsAnyOf("STOPPING", "STOPPED");
    }
  }

  private HttpResponse<String> send(HttpClient client, String method, String path, String body)
      throws IOException, InterruptedException {
    var builder =
        HttpRequest.newBuilder(uri(path))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "application/json");
    var publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
    return client.send(
        builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://127.0.0.1:" + port + path);
  }
}
