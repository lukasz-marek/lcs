package lmarek.lcs.xcs;

/** Signals that protected active rules leave no safe way to represent every legal action. */
public final class XcsCapacityException extends IllegalStateException {
  public XcsCapacityException(String message) {
    super(message);
  }
}
