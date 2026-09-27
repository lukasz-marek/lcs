package lmarek.lcs.game;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** The result of a game completed under its rules. A missing winner represents a draw. */
public record GameOutcome(@Nullable Player winner, TerminationReason reason) {
  public GameOutcome {
    Objects.requireNonNull(reason);
  }

  public static GameOutcome win(Player winner, TerminationReason reason) {
    return new GameOutcome(Objects.requireNonNull(winner), reason);
  }

  public static GameOutcome draw(TerminationReason reason) {
    return new GameOutcome(null, reason);
  }

  public Optional<Player> winningPlayer() {
    return Optional.ofNullable(winner);
  }

  public boolean isDraw() {
    return winner == null;
  }

  public double rewardFor(Player player) {
    if (winner == null) {
      return 0.0;
    }
    return winner == player ? 1.0 : -1.0;
  }
}
