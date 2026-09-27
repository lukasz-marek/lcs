package lmarek.lcs.agent;

import java.util.Objects;
import lmarek.lcs.game.Player;

public record EpisodeContext<S>(S initialState, Player seat, boolean training, long episodeNumber) {
  public EpisodeContext {
    Objects.requireNonNull(initialState);
    Objects.requireNonNull(seat);
  }
}
