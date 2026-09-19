package lmarek.lcs.classifier.rule;

public interface Matcher {
  boolean matches(String tested);

  static Matcher any() {
    return new Any();
  }

  static Matcher oneOf(String... values) {
    return new OneOf(values);
  }
}
