package lmarek.lcs.xcs;

import lmarek.lcs.game.Player;

/**
 * Converts a game-specific position and move into XCS's categorical input and stable action key.
 */
public interface StateActionEncoder<S, M> {
  CategoricalState encode(S state, Player perspective);

  String actionId(M move, Player perspective);
}
