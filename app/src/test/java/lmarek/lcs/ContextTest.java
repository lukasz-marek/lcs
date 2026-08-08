package lmarek.lcs;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContextTest {
  @Autowired private ApplicationContext applicationContext;

  @Test
  void contextStarts() {
    Assertions.assertThat(applicationContext).isNotNull();
  }
}
