package lmarek.lcs.classifier.rule;

public record Any() implements Matcher {
  @Override
  public boolean matches(String tested) {
    return true;
  }
}
