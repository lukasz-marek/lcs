package lmarek.lcs.arena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ArenaPerformanceTest {
  @Test
  void learningDefaultsToSixteenAndIsBoundedAndExposedInOptions() {
    assertThat(ArenaPerformance.defaults().learningWorkers()).isEqualTo(16);
    assertThatThrownBy(() -> new ArenaPerformance(1, 0, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ArenaPerformance(1, 17, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new ArenaService(2, 16, 128).options().performance())
        .isEqualTo(new ArenaPerformance(2, 16, 128));
  }
}
