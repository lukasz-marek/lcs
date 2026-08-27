package lmarek.lcs.classifier.learning;

import java.util.concurrent.ThreadLocalRandom;
import lmarek.lcs.classifier.data.Sample;
import lmarek.lcs.classifier.rule.Classifier;
import lmarek.lcs.classifier.rule.ClassifierBuilder;
import lmarek.lcs.classifier.rule.Condition;
import lmarek.lcs.classifier.rule.Matcher;
import lmarek.lcs.classifier.symbol.Symbol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CoveringService {
  private final double generalizationProbability;

  public CoveringService(
      @Value("${learning.generalization.probability}") double generalizationProbability) {
    if (generalizationProbability < 0 || generalizationProbability > 1) {
      throw new IllegalArgumentException("probability must be between 0 and 1");
    }
    this.generalizationProbability = generalizationProbability;
  }

  public Classifier generateClassifier(Sample sample, LearningMetadata learningMetadata) {
    var condition =
        new Condition(sample.data().values().stream().map(this::randomMatcherFor).toList());
    return new ClassifierBuilder()
        .condition(condition)
        .action(sample.action())
        .metadata(Classifier.Metadata.defaults(learningMetadata.iteration()))
        .build();
  }

  private Matcher randomMatcherFor(Symbol symbol) {
    var generalize = ThreadLocalRandom.current().nextDouble() < generalizationProbability;
    return generalize ? Matcher.any() : Matcher.oneOf(symbol);
  }
}
