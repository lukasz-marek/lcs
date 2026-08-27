package lmarek.lcs.classifier.learning;

import java.util.concurrent.ThreadLocalRandom;
import lmarek.lcs.classifier.data.SampleData;
import lmarek.lcs.classifier.rule.Action;
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

  public Classifier generateClassifier(
      SampleData sample, Action allowedAction, LearningMetadata learningMetadata) {
    var condition = new Condition(sample.values().stream().map(this::randomMatcherFor).toList());
    return new ClassifierBuilder()
        .condition(condition)
        .action(allowedAction)
        .metadata(Classifier.Metadata.defaults(learningMetadata.iteration()))
        .build();
  }

  private Matcher randomMatcherFor(Symbol symbol) {
    var generalize = ThreadLocalRandom.current().nextDouble() < generalizationProbability;
    return generalize ? Matcher.any() : Matcher.oneOf(symbol);
  }
}
