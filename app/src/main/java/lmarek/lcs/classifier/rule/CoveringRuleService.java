package lmarek.lcs.classifier.rule;

import java.util.concurrent.ThreadLocalRandom;
import lmarek.lcs.classifier.data.Sample;
import lmarek.lcs.classifier.symbol.Symbol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CoveringRuleService {
  private final double generalizationProbability;

  public CoveringRuleService(
      @Value("${learning.generalization.probability}") double generalizationProbability) {
    if (generalizationProbability < 0 || generalizationProbability > 1) {
      throw new IllegalArgumentException("probability must be between 0 and 1");
    }
    this.generalizationProbability = generalizationProbability;
  }

  public MatchableRule generateCoveringRule(Sample sample) {
    var matchers = sample.data().values().stream().map(this::randomMatcherFor).toList();
    return new MatchableRuleBuilder().matchers(matchers).prediction(sample.action()).build();
  }

  private Matcher randomMatcherFor(Symbol symbol) {
    var generalize = ThreadLocalRandom.current().nextDouble() < generalizationProbability;
    return generalize ? Matcher.any() : Matcher.oneOf(symbol);
  }
}
