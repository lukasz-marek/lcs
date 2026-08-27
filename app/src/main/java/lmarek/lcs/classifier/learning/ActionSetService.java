package lmarek.lcs.classifier.learning;

import java.util.List;
import lmarek.lcs.classifier.rule.Action;
import org.springframework.stereotype.Service;

@Service
public class ActionSetService {
  ActionSet selectAction(Action action, MatchSet matchSet) {
    var classifiers = matchSet.classifiersByPrediction().getOrDefault(action, List.of());
    return new ActionSetBuilder().action(action).classifiers(classifiers).build();
  }
}
