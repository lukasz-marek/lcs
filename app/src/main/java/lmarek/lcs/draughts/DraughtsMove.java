package lmarek.lcs.draughts;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lmarek.lcs.game.Player;

/** One complete turn: an origin and every landing/captured square in traversal order. */
public record DraughtsMove(int origin, List<Integer> landings, List<Integer> capturedSquares) {
  public DraughtsMove {
    DraughtsBoard.requireSquare(origin);
    landings = List.copyOf(landings);
    capturedSquares = List.copyOf(capturedSquares);
    if (landings.isEmpty()) {
      throw new IllegalArgumentException("A move needs at least one landing");
    }
    landings.forEach(DraughtsBoard::requireSquare);
    capturedSquares.forEach(DraughtsBoard::requireSquare);
    if (capturedSquares.isEmpty() && landings.size() != 1) {
      throw new IllegalArgumentException("A quiet move has exactly one landing");
    }
    if (!capturedSquares.isEmpty() && capturedSquares.size() != landings.size()) {
      throw new IllegalArgumentException("Every capture step needs one captured square");
    }
  }

  public static DraughtsMove quiet(int origin, int destination) {
    return new DraughtsMove(origin, List.of(destination), List.of());
  }

  public static DraughtsMove capture(
      int origin, List<Integer> landings, List<Integer> capturedSquares) {
    if (capturedSquares.isEmpty()) {
      throw new IllegalArgumentException("A capture needs a captured square");
    }
    return new DraughtsMove(origin, landings, capturedSquares);
  }

  public int destination() {
    return landings.getLast();
  }

  public boolean isCapture() {
    return !capturedSquares.isEmpty();
  }

  public String actionId() {
    var result = new StringBuilder(Integer.toString(origin));
    if (!isCapture()) {
      return result.append('-').append(destination()).toString();
    }
    landings.forEach(landing -> result.append('x').append(landing));
    result.append('[');
    for (int index = 0; index < capturedSquares.size(); index++) {
      if (index > 0) {
        result.append(',');
      }
      result.append(capturedSquares.get(index));
    }
    return result.append(']').toString();
  }

  /** Returns the same geometric move from the given player's actor-relative viewpoint. */
  public DraughtsMove orientedFor(Player player) {
    Objects.requireNonNull(player);
    if (player == Player.WHITE) {
      return this;
    }
    return new DraughtsMove(
        DraughtsBoard.rotate(origin),
        landings.stream().map(DraughtsBoard::rotate).toList(),
        capturedSquares.stream().map(DraughtsBoard::rotate).toList());
  }

  /** Parses the stable format emitted by {@link #actionId()}. */
  public static DraughtsMove parseActionId(String actionId) {
    Objects.requireNonNull(actionId);
    try {
      if (actionId.indexOf('x') >= 0) {
        var bracket = actionId.indexOf('[');
        if (bracket < 0 || !actionId.endsWith("]")) {
          throw new IllegalArgumentException("Capture action lacks captured-square suffix");
        }
        var pathParts = actionId.substring(0, bracket).split("x", -1);
        if (pathParts.length < 2) {
          throw new IllegalArgumentException("Capture action lacks a landing");
        }
        var landings = new ArrayList<Integer>();
        for (int index = 1; index < pathParts.length; index++) {
          landings.add(Integer.parseInt(pathParts[index]));
        }
        var capturesText = actionId.substring(bracket + 1, actionId.length() - 1);
        if (capturesText.isEmpty()) {
          throw new IllegalArgumentException("Capture action lacks captured squares");
        }
        var captures = new ArrayList<Integer>();
        for (var part : capturesText.split(",", -1)) {
          captures.add(Integer.parseInt(part));
        }
        return capture(Integer.parseInt(pathParts[0]), landings, captures);
      }

      var quietParts = actionId.split("-", -1);
      if (quietParts.length != 2) {
        throw new IllegalArgumentException("Invalid quiet action");
      }
      return quiet(Integer.parseInt(quietParts[0]), Integer.parseInt(quietParts[1]));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid draughts action: " + actionId, exception);
    }
  }
}
