package lmarek.lcs.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import lmarek.lcs.xcs.XcsParameters;
import lmarek.lcs.xcs.XcsPopulationSnapshot;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
final class XcsRuleSetStore {
  private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _.-]{0,63}");
  private final Path directory;
  private final ObjectMapper mapper;

  XcsRuleSetStore(
      ObjectMapper mapper,
      @Value("${arena.xcs.rule-set-directory:data/xcs-rule-sets}") String directory) {
    this.mapper = mapper;
    this.directory = Path.of(directory).toAbsolutePath().normalize();
  }

  synchronized List<RuleSetInfo> list() {
    try {
      if (!Files.exists(directory)) return List.of();
      try (var files = Files.list(directory)) {
        return files
            .filter(path -> path.getFileName().toString().endsWith(".json"))
            .map(this::read)
            .map(
                saved ->
                    new RuleSetInfo(
                        saved.name(),
                        saved.updatedAt(),
                        saved.population().classifiers().size(),
                        saved.population().iteration()))
            .sorted(Comparator.comparing(RuleSetInfo::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Could not list saved XCS rule sets", exception);
    }
  }

  synchronized @Nullable SavedRuleSet load(String name) {
    var path = path(name);
    if (!Files.exists(path)) return null;
    return read(path);
  }

  synchronized void save(String name, XcsParameters parameters, XcsPopulationSnapshot population) {
    var path = path(name);
    try {
      Files.createDirectories(directory);
      var saved = new SavedRuleSet(1, name, Instant.now().toString(), parameters, population);
      var temporary = Files.createTempFile(directory, ".xcs-", ".tmp");
      try {
        Files.write(temporary, mapper.writeValueAsBytes(saved));
        try {
          Files.move(
              temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
          Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Could not save XCS rule set '%s'".formatted(name), exception);
    }
  }

  static String validateName(String name) {
    if (name == null || !NAME.matcher(name).matches()) {
      throw new IllegalArgumentException(
          "Rule set name must be 1–64 letters, numbers, spaces, dots, dashes, or underscores");
    }
    return name;
  }

  private Path path(String name) {
    validateName(name);
    var result = directory.resolve(name + ".json").normalize();
    if (!directory.equals(result.getParent()))
      throw new IllegalArgumentException("Invalid rule set name");
    return result;
  }

  private SavedRuleSet read(Path path) {
    try {
      var saved = mapper.readValue(path.toFile(), SavedRuleSet.class);
      if (saved.version() != 1)
        throw new IllegalArgumentException("Unsupported saved rule set format version");
      validateName(saved.name());
      return saved;
    } catch (RuntimeException exception) {
      throw new IllegalStateException(
          "Could not read saved XCS rule set '%s'".formatted(path.getFileName()), exception);
    }
  }

  record SavedRuleSet(
      int version,
      String name,
      String updatedAt,
      XcsParameters parameters,
      XcsPopulationSnapshot population) {}

  record RuleSetInfo(String name, String updatedAt, int rules, long iterations) {}
}
