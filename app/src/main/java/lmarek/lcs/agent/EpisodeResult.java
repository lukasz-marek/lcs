package lmarek.lcs.agent;

import java.util.List;
import java.util.Objects;
import lmarek.lcs.game.GameOutcome;
import lmarek.lcs.game.Player;

public record EpisodeResult<S, M>(
    S initialState,
    S finalState,
    GameOutcome outcome,
    List<PlayedTurn<S, M>> turns,
    Player seat,
    boolean training,
    long episodeNumber) {
  public EpisodeResult {
    Objects.requireNonNull(initialState);
    Objects.requireNonNull(finalState);
    Objects.requireNonNull(outcome);
    turns = List.copyOf(turns);
    Objects.requireNonNull(seat);
  }

  public EpisodeResult<S, M> forSeat(Player newSeat) {
    return new EpisodeResult<>(
        initialState, finalState, outcome, turns, newSeat, training, episodeNumber);
  }

  public double reward() {
    return outcome.rewardFor(seat);
  }
}
