package lmarek.lcs.game;

import lmarek.lcs.agent.PlayedTurn;

@FunctionalInterface
public interface GameObserver<S, M> {
  void turnCompleted(PlayedTurn<S, M> turn);

  static <S, M> GameObserver<S, M> none() {
    return turn -> {};
  }
}
