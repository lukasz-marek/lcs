package lmarek.lcs.arena;

import java.util.Objects;

public record PacingRequest(Pacing pacing) {
  public PacingRequest {
    Objects.requireNonNull(pacing);
  }
}
