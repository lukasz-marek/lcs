package lmarek.lcs.agent;

import java.util.List;
import java.util.Objects;
import lmarek.lcs.game.Player;

public record DecisionContext<S, M>(
    S state, Player player, List<M> legalMoves, int ply, boolean training, long episodeNumber) {
  public DecisionContext {
    Objects.requireNonNull(state);
    Objects.requireNonNull(player);
    legalMoves = List.copyOf(legalMoves);
    if (legalMoves.isEmpty()) {
      throw new IllegalArgumentException("A decision requires at least one legal move");
    }
  }
}
