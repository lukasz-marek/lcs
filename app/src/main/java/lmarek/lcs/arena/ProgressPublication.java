package lmarek.lcs.arena;

/** Release/acquire publication of one immutable completed-worker bundle. */
final class ProgressPublication<T> {
  private volatile T current;

  ProgressPublication(T initial) {
    current = initial;
  }

  T get() {
    return current;
  }

  void publish(T next) {
    current = next;
  }
}
