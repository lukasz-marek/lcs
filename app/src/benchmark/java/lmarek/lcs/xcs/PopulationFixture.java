package lmarek.lcs.xcs;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Reflection is confined to unmeasured fixture construction; production exposes no rule importer.
 */
final class PopulationFixture {
  static final CategoricalState STATE =
      new CategoricalState(java.util.Collections.nCopies(52, "0"));

  static Object create(
      boolean baseline, int size, int actions, int numerosity, boolean highMatch, boolean histories)
      throws ReflectiveOperationException {
    var defaults = XcsParameters.defaults();
    var parameters =
        new XcsParameters(
            size,
            defaults.beta(),
            defaults.gamma(),
            .5,
            .1,
            .01,
            5,
            .33,
            0,
            0,
            .01,
            25,
            .8,
            .04,
            20,
            .1,
            20,
            true);
    Object population =
        baseline
            ? new ReferenceXcsPopulation(parameters, new SplittableRandom(7))
            : new XcsPopulation(parameters, new SplittableRandom(7));
    var type = population.getClass();
    var ruleType = Class.forName(type.getName() + "$Rule");
    Constructor<?> ruleConstructor = ruleType.getDeclaredConstructors()[0];
    ruleConstructor.setAccessible(true);
    var rulesField = type.getDeclaredField("rules");
    rulesField.setAccessible(true);
    @SuppressWarnings("unchecked")
    var rules = (Collection<Object>) rulesField.get(population);
    var add = baseline ? null : type.getDeclaredMethod("addRule", ruleType);
    if (add != null) add.setAccessible(true);
    Constructor<?> termConstructor =
        baseline ? Class.forName(type.getName() + "$Term").getDeclaredConstructors()[0] : null;
    if (termConstructor != null) termConstructor.setAccessible(true);
    Object wildcard = null;
    if (!baseline) {
      var field = type.getDeclaredField("WILDCARD");
      field.setAccessible(true);
      wildcard = field.get(null);
    }
    int macroCount = size / numerosity;
    var ids = new ArrayList<Long>(macroCount);
    for (int i = 0; i < macroCount; i++) {
      var values = new Object[52];
      var terms = new ArrayList<Object>(52);
      for (int j = 0; j < 52; j++) {
        boolean any = highMatch && j < 51;
        String value = j == 51 || ((i >>> (j % 20)) & 1) == 0 ? "0" : "1";
        if (baseline)
          terms.add(
              java.util.Objects.requireNonNull(termConstructor).newInstance(any, any ? "" : value));
        else values[j] = any ? wildcard : value;
      }
      long id = i + 1L;
      var rule =
          ruleConstructor.newInstance(
              id,
              baseline ? terms : values,
              "a" + (i % actions),
              0.0,
              0.0,
              .01,
              0L,
              numerosity,
              1.0,
              0L,
              List.of(),
              XcsBirthReason.COVERING);
      if (add == null) rules.add(rule);
      else add.invoke(population, rule);
      ids.add(id);
    }
    var nextId = type.getDeclaredField("nextRuleId");
    nextId.setAccessible(true);
    nextId.setLong(population, macroCount + 1L);
    if (histories) {
      for (int from = 0; from < ids.size(); from += 512) {
        var batch = ids.subList(from, Math.min(ids.size(), from + 512));
        for (int change = 0; change < 50; change++) {
          if (population instanceof XcsPopulation optimized) optimized.update(batch, change % 2);
          else ((ReferenceXcsPopulation) population).update(batch, change % 2);
        }
      }
    }
    return population;
  }
}
