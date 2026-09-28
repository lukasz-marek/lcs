const ui = {
  status: document.querySelector("#status-label"),
  dot: document.querySelector("#status-dot"),
  start: document.querySelector("#start"),
  pause: document.querySelector("#pause"),
  stop: document.querySelector("#stop"),
  board: document.querySelector("#board"),
  toast: document.querySelector("#toast"),
  chart: document.querySelector("#main-chart"),
  pacing: document.querySelector("#pacing-choice"),
  replayPicker: document.querySelector("#replay-picker"),
  replaySlider: document.querySelector("#replay-slider"),
  replayPlay: document.querySelector("#replay-play"),
  ruleCompetitor: document.querySelector("#rule-competitor"),
  ruleId: document.querySelector("#rule-id"),
};

const ACTIVE_STATUSES = new Set(["RUNNING", "PAUSING", "PAUSED", "STOPPING"]);
const CONTROLLABLE_STATUSES = new Set(["RUNNING", "PAUSING", "PAUSED"]);
const LONG_MIN = -9223372036854775808n;
const LONG_MAX = 9223372036854775807n;
const SETTING_HELP = {
  maximumPopulation: "Maximum number of micro-classifiers this XCS population can retain. A micro-classifier is one copy of a rule; duplicate rules increase numerosity.",
  beta: "Learning rate for updating a rule's prediction, prediction error, and action-set size. Larger values adapt faster but make estimates more sensitive to recent outcomes.",
  gamma: "Discount applied to future rewards when XCS updates predictions. Lower values favor immediate rewards; values near 1 give more weight to long-term outcomes.",
  explorationProbability: "Chance that XCS chooses a random legal move instead of its current best predicted move, so it can keep exploring alternatives.",
  wildcardProbability: "Chance that each condition attribute created by covering is left unrestricted. Higher values create broader rules that match more board states.",
  simulations: "Number of simulated continuations UCT search evaluates for each move. More simulations usually improve search quality and take longer.",
  rolloutPlyLimit: "Maximum number of plies in one simulated continuation. At the cap, search estimates the position without simulating further.",
  evaluationInterval: "When either competitor learns, run a frozen evaluation series after every configured number of completed training games. Evaluation games do not update the learners or training score.",
  evaluationGames: "Number of frozen games in each evaluation series. An even count gives both competitors the same number of games as White and Black.",
};
const METHOD_HELP = {
  XCS: "Learns condition-action rules from rewards and uses their predictions to choose moves.",
  MCTS: "Uses UCT Monte Carlo tree search to compare simulated continuations before choosing a move.",
  RANDOM: "Chooses uniformly from the currently legal moves; this is a non-learning baseline.",
};
const PRESET_HELP = {
  FAST: "Uses 100 simulations per move for quicker, lighter search.",
  BALANCED: "Uses 500 simulations per move as a balance between search quality and speed.",
  STRONG: "Uses 2,000 simulations per move for deeper search at higher compute cost.",
  STANDARD: "Standard XCS learning configuration. Adjust the advanced values to change its learning behavior.",
  UNIFORM: "Uniform random choice among legal moves.",
};

const state = {
  options: null,
  snapshot: null,
  pacing: "LIVE",
  chart: "results",
  source: null,
  sourceRunId: null,
  sourceErrorTimer: null,
  refreshInFlight: false,
  refreshQueued: false,
  renderPending: false,
  fullRenderPending: false,
  history: { charts: {}, recentReplays: [], evolutionHighlights: [] },
  historyRevisions: { charts: -1, replays: -1, evolution: -1 },
  historyRequest: null,
  historyRetryTimer: null,
  rendered: {},
  epoch: 0,
  startPending: false,
  startToken: 0,
  replay: null,
  replayGameNumber: null,
  replayPly: 0,
  replayTimer: null,
  replayToken: 0,
  viewMode: "LIVE",
  ruleToken: 0,
  shownErrorKey: null,
  toastTimer: null,
};

class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

function squareId(row, column) {
  return (row + column) % 2 === 1 ? row * 5 + Math.floor(column / 2) + 1 : null;
}

function makeBoard() {
  for (let row = 0; row < 10; row += 1) {
    for (let column = 0; column < 10; column += 1) {
      const cell = document.createElement("div");
      const id = squareId(row, column);
      cell.className = `square${id ? " playable" : ""}`;
      cell.dataset.square = id || "";
      cell.setAttribute("role", "gridcell");
      if (id) {
        const number = document.createElement("span");
        number.className = "square-number";
        number.textContent = id;
        cell.append(number);
      }
      ui.board.append(cell);
    }
  }
}

async function json(url, options = {}) {
  const headers = { Accept: "application/json", ...(options.headers || {}) };
  if (options.body != null) headers["Content-Type"] = "application/json";
  const response = await fetch(url, { cache: "no-store", ...options, headers });
  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch (_) {
      body = text;
    }
  }
  if (!response.ok) {
    const message =
      (body && typeof body === "object" && (body.error || body.message || body.detail || body.title)) ||
      (typeof body === "string" && body.trim()) ||
      `${response.status} ${response.statusText}`;
    throw new ApiError(String(message), response.status);
  }
  return body;
}

function showToast(message) {
  clearTimeout(state.toastTimer);
  ui.toast.textContent = message;
  ui.toast.classList.add("show");
  state.toastTimer = setTimeout(() => ui.toast.classList.remove("show"), 3500);
}

function title(text) {
  const value = String(text || "");
  return value ? value.charAt(0) + value.slice(1).toLowerCase().replaceAll("_", " ") : "";
}

function option(value, label = value) {
  const element = document.createElement("option");
  element.value = value;
  element.textContent = label;
  return element;
}

function setText(id, text) {
  document.querySelector(`#${id}`).textContent = text;
}

function helpMarker(message) {
  const marker = document.createElement("span");
  marker.className = "field-help";
  marker.tabIndex = 0;
  marker.setAttribute("role", "note");
  marker.setAttribute("aria-label", `Help: ${message}`);
  marker.dataset.tooltip = message;
  return marker;
}

function applySetting(input, setting, value = setting.defaultValue) {
  input.value = String(value);
  input.step = String(setting.step);
  if (setting.minimum != null) input.min = String(setting.minimum);
  else input.removeAttribute("min");
  if (setting.maximum != null) input.max = String(setting.maximum);
  else input.removeAttribute("max");
  input.dataset.integral = String(Boolean(setting.integral));
  input.dataset.even = String(Boolean(setting.even));
  const description =
    SETTING_HELP[setting.id] ||
    `${setting.label}. Allowed range: ${setting.minimum} to ${setting.maximum}.`;
  input.title = description;
}

function readSetting(input, setting) {
  const value = Number(input.value);
  if (!input.value.trim() || !Number.isFinite(value)) {
    throw new Error(`${setting.label} must be a number`);
  }
  if (setting.minimum != null && value < setting.minimum) {
    throw new Error(`${setting.label} must be at least ${setting.minimum}`);
  }
  if (setting.maximum != null && value > setting.maximum) {
    throw new Error(`${setting.label} must be at most ${setting.maximum}`);
  }
  if (setting.integral && !Number.isInteger(value)) {
    throw new Error(`${setting.label} must be a whole number`);
  }
  if (setting.even && value % 2 !== 0) {
    throw new Error(`${setting.label} must be even`);
  }
  return value;
}

function setupAgent(side) {
  const kind = document.querySelector(`#agent-${side}-kind`);
  const preset = document.querySelector(`#agent-${side}-preset`);
  kind.replaceChildren(...state.options.agentKinds.map((item) => option(item.id, item.label)));
  const preferred = side === "a" ? "XCS" : "MCTS";
  kind.value = state.options.agentKinds.some((item) => item.id === preferred)
    ? preferred
    : state.options.agentKinds[0]?.id || "";

  const renderAdvanced = (current) => {
    const presetSettings = current.presets.find((item) => item.id === preset.value)?.settings || {};
    const advanced = document.querySelector(`#agent-${side}-advanced`);
    advanced.replaceChildren(
      ...current.settings.map((setting) => {
        const label = document.createElement("label");
        label.append(
          document.createTextNode(setting.label),
          helpMarker(SETTING_HELP[setting.id] || `${setting.label}.`),
        );
        const input = document.createElement("input");
        input.name = setting.id;
        input.type = "number";
        applySetting(input, setting, presetSettings[setting.id] ?? setting.defaultValue);
        label.append(input);
        return label;
      }),
    );
  };

  const refresh = () => {
    const current = state.options.agentKinds.find((item) => item.id === kind.value);
    if (!current) return;
    kind.title = METHOD_HELP[current.id] || current.label;
    preset.replaceChildren(...current.presets.map((item) => option(item.id, item.label)));
    preset.value = current.defaultPreset;
    preset.title =
      PRESET_HELP[preset.value] ||
      `Preset: ${preset.selectedOptions[0]?.textContent || preset.value}.`;
    renderAdvanced(current);
  };

  preset.addEventListener("change", () => {
    const current = state.options.agentKinds.find((item) => item.id === kind.value);
    if (current) {
      preset.title =
        PRESET_HELP[preset.value] ||
        `Preset: ${preset.selectedOptions[0]?.textContent || preset.value}.`;
      renderAdvanced(current);
    }
  });
  kind.addEventListener("change", refresh);
  refresh();
}

function agentRequest(side) {
  const kindId = document.querySelector(`#agent-${side}-kind`).value;
  const kind = state.options.agentKinds.find((item) => item.id === kindId);
  if (!kind) throw new Error(`Choose competitor ${side.toUpperCase()}'s method`);
  const settings = {};
  kind.settings.forEach((setting) => {
    const input = document.querySelector(`#agent-${side}-advanced input[name="${setting.id}"]`);
    settings[setting.id] = readSetting(input, setting);
  });
  return {
    kind: kindId,
    preset: document.querySelector(`#agent-${side}-preset`).value,
    settings,
  };
}

function paceCopy(mode) {
  if (mode === "LIVE") return ["Live", "600 ms"];
  if (mode === "TURBO") return ["Turbo", "full speed"];
  return [title(mode), ""];
}

function setupPacing() {
  const modes = state.options.pacingModes;
  state.pacing = modes.includes("LIVE") ? "LIVE" : modes[0];
  ui.pacing.replaceChildren(
    ...modes.map((mode) => {
      const [label, detail] = paceCopy(mode);
      const button = document.createElement("button");
      button.className = "pace";
      button.dataset.pacing = mode;
      button.type = "button";
      button.title =
        mode === "LIVE"
          ? "Pause briefly between training moves so the game is easier to watch."
          : "Run training games without a delay between moves.";
      button.append(document.createTextNode(label));
      if (detail) {
        const small = document.createElement("small");
        small.textContent = detail;
        button.append(small);
      }
      button.addEventListener("click", () => selectPacing(mode));
      return button;
    }),
  );
  syncPacingButtons();
}

async function loadOptions() {
  state.options = await json("/api/arena/options");
  setupAgent("a");
  setupAgent("b");
  document.querySelector("#seed").value = state.options.defaultSeed;
  applySetting(document.querySelector("#evaluation-interval"), state.options.evaluationInterval);
  applySetting(document.querySelector("#evaluation-games"), state.options.evaluationGames);
  setupPacing();
  scheduleRender();
}

function readSeed() {
  const value = document.querySelector("#seed").value.trim();
  if (!/^-?\d+$/.test(value)) throw new Error("Seed must be a whole decimal number");
  let parsed;
  try {
    parsed = BigInt(value);
  } catch (_) {
    throw new Error("Seed must be a whole decimal number");
  }
  if (parsed < LONG_MIN || parsed > LONG_MAX) throw new Error("Seed must fit in a signed 64-bit integer");
  return parsed.toString();
}

function startRequest() {
  return {
    agentA: agentRequest("a"),
    agentB: agentRequest("b"),
    seed: readSeed(),
    pacing: state.pacing,
    evaluationInterval: readSetting(
      document.querySelector("#evaluation-interval"),
      state.options.evaluationInterval,
    ),
    evaluationGames: readSetting(
      document.querySelector("#evaluation-games"),
      state.options.evaluationGames,
    ),
  };
}

async function startRun() {
  let body;
  try {
    body = startRequest();
  } catch (error) {
    showToast(error.message);
    return;
  }

  const token = ++state.startToken;
  state.startPending = true;
  scheduleRender();
  try {
    const snapshot = await json("/api/arena/runs", {
      method: "POST",
      body: JSON.stringify(body),
    });
    if (token !== state.startToken) return;
    acceptSnapshot(snapshot);
  } catch (error) {
    if (token === state.startToken) showToast(error.message);
  } finally {
    if (token === state.startToken) {
      state.startPending = false;
      scheduleRender();
    }
  }
}

async function command(name, method = "POST", body = null) {
  const runId = state.snapshot?.runId;
  const epoch = state.epoch;
  if (!runId) return;
  try {
    const snapshot = await json(`/api/arena/runs/current/${name}`, {
      method,
      body: body == null ? null : JSON.stringify(body),
    });
    if (epoch !== state.epoch || state.snapshot?.runId !== runId) return;
    acceptSnapshot(snapshot);
  } catch (error) {
    if (epoch === state.epoch && state.snapshot?.runId === runId) {
      showToast(error.message);
      syncSnapshotPacing();
    }
  }
}

function compareRevision(left, right) {
  try {
    const leftRevision = BigInt(String(left));
    const rightRevision = BigInt(String(right));
    return leftRevision < rightRevision ? -1 : leftRevision > rightRevision ? 1 : 0;
  } catch (_) {
    return Number(left) - Number(right);
  }
}

function acceptSnapshot(snapshot) {
  if (!snapshot?.runId) return false;
  const current = state.snapshot;
  if (current?.runId === snapshot.runId && compareRevision(snapshot.revision, current.revision) <= 0) {
    return false;
  }
  if (!current || current.runId !== snapshot.runId) resetForRun();
  state.snapshot = snapshot;
  acceptHistory(snapshot);
  queueHistoryRefresh();
  syncSnapshotPacing();
  scheduleRender();
  ensureEvents();
  return true;
}

const HISTORY_SECTIONS = {
  charts: "charts",
  replays: "recentReplays",
  evolution: "evolutionHighlights",
};

function acceptHistory(response) {
  if (response.runId !== state.snapshot?.runId || !response.historyRevisions) return;
  for (const [section, field] of Object.entries(HISTORY_SECTIONS)) {
    if (response[field] != null &&
        compareRevision(response.historyRevisions[section], state.historyRevisions[section]) >= 0) {
      if (compareRevision(response.historyRevisions[section], state.historyRevisions[section]) > 0) {
        state.history[field] = response[field];
      }
      state.historyRevisions[section] = response.historyRevisions[section];
    }
  }
}

function queueHistoryRefresh() {
  const snapshot = state.snapshot;
  if (!snapshot?.historyRevisions || state.historyRequest) return;
  const changed = Object.keys(HISTORY_SECTIONS).some((section) =>
    compareRevision(snapshot.historyRevisions[section], state.historyRevisions[section]) > 0);
  if (!changed) return;
  clearTimeout(state.historyRetryTimer);
  const request = { epoch: state.epoch, runId: snapshot.runId };
  state.historyRequest = request;
  const params = new URLSearchParams({ runId: request.runId, ...state.historyRevisions });
  let failed = false;
  json(`/api/arena/runs/current/history?${params}`)
    .then((response) => {
      if (request !== state.historyRequest || request.epoch !== state.epoch) return;
      if (response.runId !== request.runId) {
        failed = true;
        queueSnapshotRefresh();
        return;
      }
      acceptHistory(response);
      scheduleRender();
    })
    .catch(() => { failed = true; })
    .finally(() => {
      if (request !== state.historyRequest) return;
      state.historyRequest = null;
      // Retry even after a terminal update, when no further SSE notification will arrive.
      if (failed) state.historyRetryTimer = setTimeout(queueHistoryRefresh, 1000);
      else queueHistoryRefresh();
    });
}

function queueSnapshotRefresh(source = null) {
  if (source && source !== state.source) return;
  if (state.refreshInFlight) {
    state.refreshQueued = true;
    return;
  }

  state.refreshInFlight = true;
  const epoch = state.epoch;
  const sourceAtStart = source;
  json("/api/arena/runs/current/live")
    .then((snapshot) => {
      if (epoch !== state.epoch) return;
      if (sourceAtStart && sourceAtStart !== state.source) return;
      acceptSnapshot(snapshot);
    })
    .catch((error) => {
      if (error.status !== 404 && epoch === state.epoch) showToast(error.message);
    })
    .finally(() => {
      state.refreshInFlight = false;
      if (state.refreshQueued) {
        state.refreshQueued = false;
        queueSnapshotRefresh();
      }
    });
}

function disconnectEvents() {
  clearTimeout(state.sourceErrorTimer);
  state.sourceErrorTimer = null;
  if (state.source) state.source.close();
  state.source = null;
  state.sourceRunId = null;
}

function ensureEvents() {
  const snapshot = state.snapshot;
  if (!snapshot || !ACTIVE_STATUSES.has(snapshot.status)) {
    disconnectEvents();
    return;
  }
  if (state.source && state.sourceRunId === snapshot.runId) return;

  disconnectEvents();
  const source = new EventSource("/api/arena/runs/current/events");
  state.source = source;
  state.sourceRunId = snapshot.runId;
  source.addEventListener("update", (event) => {
    if (source !== state.source) return;
    let notification = null;
    try {
      notification = JSON.parse(event.data);
    } catch (_) {
      // A malformed notification still means the snapshot may have changed.
    }
    if (
      notification?.runId === state.snapshot?.runId &&
      compareRevision(notification.revision, state.snapshot?.revision) <= 0
    ) {
      return;
    }
    queueSnapshotRefresh(source);
  });
  source.onerror = () => {
    if (source !== state.source) return;
    clearTimeout(state.sourceErrorTimer);
    state.sourceErrorTimer = setTimeout(() => queueSnapshotRefresh(source), 750);
    // EventSource owns reconnection; retaining this instance preserves Last-Event-ID.
  };
}

function clearReplayTimer() {
  clearInterval(state.replayTimer);
  state.replayTimer = null;
  ui.replayPlay.textContent = "Play";
}

function resetForRun() {
  state.epoch += 1;
  state.startToken += 1;
  state.startPending = false;
  state.replayToken += 1;
  state.ruleToken += 1;
  state.refreshQueued = false;
  state.shownErrorKey = null;
  disconnectEvents();
  clearReplayTimer();
  state.snapshot = null;
  clearTimeout(state.historyRetryTimer);
  state.historyRequest = null;
  state.history = { charts: {}, recentReplays: [], evolutionHighlights: [] };
  state.historyRevisions = { charts: -1, replays: -1, evolution: -1 };
  state.rendered = {};
  state.replay = null;
  state.replayGameNumber = null;
  state.replayPly = 0;
  state.viewMode = "LIVE";
  clearRunUi();
}

function clearRunUi() {
  setText("training-count", "0 games");
  setText("a-wins", "0");
  setText("b-wins", "0");
  setText("draws", "0");
  setText("rolling", "—");
  setText("average-length", "—");
  setText("throughput", "—");
  setText("population-size", "0 micro rules");
  setText("game-mode", "WAITING FOR A GAME");
  setText("game-title", "Game —");
  setText("white-name", "White");
  setText("white-kind", "—");
  setText("black-name", "Black");
  setText("black-kind", "—");
  setText("move-caption", "The first move will appear here.");
  setText("replay-caption", "Completed games remain available for this run.");
  renderBoard(null);
  renderDecision(null, null);
  drawLines([]);
  document.querySelector("#evolution-feed").replaceChildren(emptyItem("Population events will appear here."));
  clearInspector("Select a retained XCS rule to inspect it.");
  ui.ruleCompetitor.replaceChildren(option("", "No inspectable competitor"));
  ui.ruleCompetitor.disabled = true;
  ui.ruleId.value = "";
  renderReplayPicker([]);
  updateReplayControls();
}

function scheduleRender(full = true) {
  state.fullRenderPending ||= full;
  if (state.renderPending) return;
  state.renderPending = true;
  requestAnimationFrame(() => {
    state.renderPending = false;
    const full = state.fullRenderPending;
    state.fullRenderPending = false;
    if (full) render();
    else if (state.viewMode === "REPLAY") renderReplayGame();
  });
}

function syncSnapshotPacing() {
  if (state.snapshot?.pacing) state.pacing = state.snapshot.pacing;
  syncPacingButtons();
}

function syncPacingButtons() {
  ui.pacing.querySelectorAll(".pace").forEach((button) => {
    button.classList.toggle("active", button.dataset.pacing === state.pacing);
    button.disabled = state.snapshot?.status === "STOPPING";
  });
}

function selectPacing(mode) {
  if (!state.options?.pacingModes.includes(mode)) return;
  const previous = state.pacing;
  state.pacing = mode;
  syncPacingButtons();
  if (state.snapshot && CONTROLLABLE_STATUSES.has(state.snapshot.status)) {
    command("pacing", "PATCH", { pacing: mode });
  } else if (state.snapshot && ACTIVE_STATUSES.has(state.snapshot.status)) {
    state.pacing = previous;
    syncPacingButtons();
  }
}

function renderBoard(board, lastMove = null) {
  const pieces = new Map();
  if (board) {
    (board.whiteMen || []).forEach((square) => pieces.set(square, ["white", false]));
    (board.whiteKings || []).forEach((square) => pieces.set(square, ["white", true]));
    (board.blackMen || []).forEach((square) => pieces.set(square, ["black", false]));
    (board.blackKings || []).forEach((square) => pieces.set(square, ["black", true]));
  }
  ui.board.querySelectorAll(".playable").forEach((cell) => {
    const id = Number(cell.dataset.square);
    const existingPiece = cell.querySelector(".piece");
    cell.classList.toggle(
      "last",
      Boolean(lastMove && (lastMove.origin === id || lastMove.landings?.includes(id))),
    );
    cell.classList.toggle("captured", Boolean(lastMove?.capturedSquares?.includes(id)));
    const item = pieces.get(id);
    cell.setAttribute("aria-label", `Square ${id}: ${item ? `${item[0]} ${item[1] ? "king" : "man"}` : "empty"}`);
    if (item) {
      const className = `piece ${item[0]}${item[1] ? " king" : ""}`;
      if (existingPiece?.className !== className) {
        existingPiece?.remove();
        const piece = document.createElement("div");
        piece.className = className;
        cell.append(piece);
      }
    } else {
      existingPiece?.remove();
    }
  });
}

function renderDecision(trace, method) {
  const container = document.querySelector("#decision-bars");
  container.replaceChildren();
  const algorithm = trace?.algorithm || method;
  document.querySelector("#decision-method").textContent = algorithm || "—";
  const help = document.querySelector("#decision-help");
  if (!trace) {
    help.textContent = "Decision evidence appears after an agent moves.";
    return;
  }

  if (trace.algorithm === "XCS") {
    const exploration = trace.exploratory ? "exploratory" : "greedy";
    help.textContent = `${exploration} choice · ${trace.matchingRuleCount ?? 0} matching rules · ${trace.actionSetRuleCount ?? 0} selected-action rules · ${trace.unknownLegalActionCount ?? 0} unknown legal actions`;
  } else if (trace.algorithm === "MCTS") {
    help.textContent = `${trace.simulations ?? 0} simulations · ${trace.truncatedRollouts ?? 0} truncated rollouts · bars show root visits`;
  } else {
    help.textContent = `Uniform choice among ${trace.legalMoveCount ?? 0} legal moves.`;
  }

  if (trace.algorithm === "XCS") help.textContent += " · action squares use the acting player's viewpoint";
  if (trace.legalMoveCount > (trace.actions || []).length && trace.algorithm !== "RANDOM") {
    help.textContent += ` · showing ${(trace.actions || []).length} of ${trace.legalMoveCount} actions, including the choice`;
  }
  const actions = (trace.actions || []).slice(0, 16);
  if (!actions.length) return;
  const value = (action) =>
    trace.algorithm === "MCTS" ? Number(action.visits ?? 0) : Number(action.prediction ?? 0);
  const maximum = Math.max(...actions.map((action) => Math.abs(value(action))), 0.0001);
  actions.forEach((action) => {
    const amount = value(action);
    const row = document.createElement("div");
    row.className = `decision-bar${action.actionId === trace.selectedAction ? " selected" : ""}`;
    if (action.meanValue != null) row.title = `Mean value ${Number(action.meanValue).toFixed(3)}`;
    const name = document.createElement("span");
    name.textContent = action.actionId;
    const track = document.createElement("span");
    track.className = "bar-track";
    const fill = document.createElement("span");
    fill.className = "bar-fill";
    fill.style.width = `${Math.max(2, (Math.abs(amount) / maximum) * 100)}%`;
    track.append(fill);
    const number = document.createElement("b");
    number.textContent = Number.isInteger(amount) ? String(amount) : amount.toFixed(3);
    row.append(name, track, number);
    container.append(row);
  });
}

function renderStatusAndControls(snapshot) {
  ui.status.textContent = snapshot ? snapshot.status.replaceAll("_", " ") : "IDLE";
  ui.dot.className = `status-dot ${snapshot ? snapshot.status.toLowerCase() : "idle"}`;
  const error = document.querySelector("#run-error");
  error.hidden = !snapshot?.lastError;
  error.textContent = snapshot?.lastError || "";
  const status = snapshot?.status;
  ui.start.disabled = !state.options || state.startPending || ACTIVE_STATUSES.has(status);
  ui.pause.disabled = !CONTROLLABLE_STATUSES.has(status);
  ui.stop.disabled = !CONTROLLABLE_STATUSES.has(status);
  ui.pause.textContent = status === "PAUSED" || status === "PAUSING" ? "Resume" : "Pause";
  syncPacingButtons();
}

function render() {
  const snapshot = state.snapshot;
  renderStatusAndControls(snapshot);
  if (!snapshot) return;

  setText("training-count", `${snapshot.trainingGames} games`);
  setText("a-wins", snapshot.statistics?.aWins ?? 0);
  setText("b-wins", snapshot.statistics?.bWins ?? 0);
  setText("draws", snapshot.statistics?.draws ?? 0);
  setText("rolling", snapshot.statistics?.rollingLabel || "—");
  setText(
    "average-length",
    snapshot.statistics?.averageLength == null
      ? "—"
      : `${snapshot.statistics.averageLength.toFixed(1)} plies`,
  );
  setText(
    "throughput",
    snapshot.statistics?.gamesPerSecond == null
      ? "—"
      : `${snapshot.statistics.gamesPerSecond.toFixed(1)} g/s`,
  );

  const competitorKey = JSON.stringify(snapshot.competitors || []);
  if (state.rendered.competitors !== competitorKey) {
    renderCompetitorSelector(snapshot.competitors || []);
    state.rendered.competitors = competitorKey;
  }
  if (state.rendered.charts !== state.history.charts) renderChart();
  const replaySelection = `${state.viewMode}:${state.replayGameNumber}`;
  if (state.rendered.replays !== state.history.recentReplays ||
      state.rendered.replaySelection !== replaySelection) {
    renderReplayPicker(state.history.recentReplays);
    state.rendered.replays = state.history.recentReplays;
    state.rendered.replaySelection = replaySelection;
  }
  if (state.rendered.evolution !== state.history.evolutionHighlights) {
    renderEvolution(state.history.evolutionHighlights);
    state.rendered.evolution = state.history.evolutionHighlights;
  }
  const population = Object.values(snapshot.telemetry || {}).reduce(
    (total, telemetry) => total + Number(telemetry?.["xcs.population.micro"] || 0),
    0,
  );
  setText("population-size", `${Math.round(population).toLocaleString()} micro rules`);

  if (state.viewMode === "REPLAY") renderReplayGame();
  else renderLiveGame(snapshot.currentGame);

  if (snapshot.lastError) {
    const key = `${snapshot.runId}:${snapshot.revision}:${snapshot.lastError}`;
    if (key !== state.shownErrorKey) {
      state.shownErrorKey = key;
      showToast(snapshot.lastError);
    }
  }
}

function renderLiveGame(game) {
  if (!game) {
    setText("game-title", "Between games");
    setText("game-mode", "LIVE BOARD");
    setText("white-name", "White");
    setText("white-kind", "—");
    setText("black-name", "Black");
    setText("black-kind", "—");
    setText("move-caption", "Waiting for the next game.");
    renderBoard(null);
    renderDecision(null, null);
    return;
  }
  setText("game-title", `Game ${game.gameNumber}`);
  setText("game-mode", game.evaluation ? "FROZEN EVALUATION" : "LIVE TRAINING");
  setText("white-name", game.whiteLabel);
  setText("white-kind", game.whiteKind);
  setText("black-name", game.blackLabel);
  setText("black-kind", game.blackKind);
  renderBoard(game.board, game.lastMove);
  setText(
    "move-caption",
    game.lastMove
      ? `Ply ${game.ply}: ${game.lastMove.notation || game.lastMove.actionId}`
      : "Opening position",
  );
  renderDecision(game.lastTrace, game.lastAgentKind);
}

const SERIES_STYLES = new Map([
  ["A wins", "#47d7d0"],
  ["B wins", "#ff6f61"],
  ["Draws", "#e7bf69"],
  ["A score", "#47d7d0"],
  ["B score", "#ff6f61"],
  ["A population", "#47d7d0"],
  ["B population", "#ff6f61"],
  ["A generality", "#93d27d"],
  ["B generality", "#f39a75"],
  ["A error", "#71a7e0"],
  ["B error", "#db79b8"],
  ["A covering", "#e7bf69"],
  ["B covering", "#ad8de2"],
]);

function seriesColor(label) {
  if (SERIES_STYLES.has(label)) return SERIES_STYLES.get(label);
  let hash = 0;
  for (const character of label) hash = (hash * 31 + character.charCodeAt(0)) >>> 0;
  const palette = ["#47d7d0", "#ff6f61", "#e7bf69", "#93d27d", "#71a7e0", "#ad8de2"];
  return palette[hash % palette.length];
}

function drawLines(series) {
  const canvas = ui.chart;
  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  const width = Math.max(300, rect.width || 300);
  const height = 210;
  canvas.width = width * dpr;
  canvas.height = height * dpr;
  const context = canvas.getContext("2d");
  context.scale(dpr, dpr);
  const padding = 45;
  context.clearRect(0, 0, width, height);
  context.strokeStyle = "#343a3d";
  context.lineWidth = 1;
  for (let index = 0; index < 5; index += 1) {
    const y = padding + ((height - 2 * padding) * index) / 4;
    context.beginPath();
    context.moveTo(padding, y);
    context.lineTo(width - padding, y);
    context.stroke();
  }

  let minimum = 0;
  let maximum = 1;
  let maximumX = 1;
  series.forEach((line) =>
    line.values.forEach((point) => {
      minimum = Math.min(minimum, point.value);
      maximum = Math.max(maximum, point.value);
      maximumX = Math.max(maximumX, point.x);
    }),
  );
  series.forEach((line) => {
    context.strokeStyle = line.color;
    context.lineWidth = 2;
    context.beginPath();
    line.values.forEach((point, index) => {
      const x = padding + ((width - 2 * padding) * point.x) / maximumX;
      const y =
        height -
        padding -
        ((height - 2 * padding) * (point.value - minimum)) / (maximum - minimum || 1);
      if (index) context.lineTo(x, y);
      else context.moveTo(x, y);
    });
    context.stroke();
    if (line.values.length === 1) {
      const point = line.values[0];
      const x = padding + ((width - 2 * padding) * point.x) / maximumX;
      const y = height - padding - ((height - 2 * padding) * (point.value - minimum)) / (maximum - minimum || 1);
      context.fillStyle = line.color;
      context.beginPath();
      context.arc(x, y, 3, 0, 2 * Math.PI);
      context.fill();
    }
  });

  context.fillStyle = "#bbb";
  context.font = "10px sans-serif";
  context.textAlign = "right";
  for (let index = 0; index < 5; index += 1) {
    const value = maximum - ((maximum - minimum) * index) / 4;
    context.fillText(Number(value.toPrecision(3)).toString(), padding - 5,
      padding + ((height - 2 * padding) * index) / 4 + 3);
  }
  context.textAlign = "left";
  context.fillText("0", padding, height - padding + 15);
  context.textAlign = "right";
  context.fillText(String(maximumX), width - padding, height - padding + 15);
  context.textAlign = "center";
  context.fillText("Completed training games", width / 2, height - 6);

  const legend = document.querySelector("#chart-legend");
  legend.replaceChildren(
    ...series.map((line) => {
      const item = document.createElement("span");
      const swatch = document.createElement("i");
      swatch.className = "legend-swatch";
      swatch.style.background = line.color;
      item.append(swatch, document.createTextNode(line.label));
      return item;
    }),
  );
}

function renderChart() {
  const data = state.history.charts[state.chart] || {};
  state.rendered.charts = state.history.charts;
  document.querySelector("#learning-metric-label").hidden = state.chart !== "learning";
  const metric = document.querySelector("#learning-metric").value;
  const series = Object.entries(data)
    .filter(([label]) => state.chart !== "learning" || label.endsWith(` ${metric}`))
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([label, values]) => ({ label, color: seriesColor(label), values }));
  drawLines(series);
}

function replayValue(gameNumber) {
  return `REPLAY:${gameNumber}`;
}

function renderReplayPicker(replays) {
  const choices = [option("LIVE", "Live board")];
  replays.forEach((replay) => {
    const mode = replay.evaluation ? "evaluation" : "training";
    choices.push(
      option(
        replayValue(replay.gameNumber),
        `#${replay.gameNumber} · ${mode} · ${replay.result} · ${replay.length} plies`,
      ),
    );
  });
  if (
    state.viewMode === "REPLAY" &&
    state.replayGameNumber != null &&
    !replays.some((replay) => String(replay.gameNumber) === String(state.replayGameNumber))
  ) {
    choices.push(option(replayValue(state.replayGameNumber), `#${state.replayGameNumber} · loaded replay`));
  }
  ui.replayPicker.replaceChildren(...choices);
  ui.replayPicker.value =
    state.viewMode === "REPLAY" && state.replayGameNumber != null
      ? replayValue(state.replayGameNumber)
      : "LIVE";
  updateReplayControls();
}

function emptyItem(message) {
  const item = document.createElement("li");
  item.className = "empty-state";
  item.textContent = message;
  return item;
}

function renderEvolution(events) {
  const feed = document.querySelector("#evolution-feed");
  if (!events.length) {
    feed.replaceChildren(emptyItem("Population events will appear here."));
    return;
  }
  feed.replaceChildren(
    ...events
      .slice()
      .reverse()
      .map((event) => {
        const item = document.createElement("li");
        const time = document.createElement("time");
        const body = document.createElement("span");
        time.textContent = `G${event.gameNumber}`;
        if (event.ruleId) {
          const button = document.createElement("button");
          button.type = "button";
          button.textContent = `rule #${event.ruleId} · ${event.message}`;
          button.addEventListener("click", () => inspectRule(event.competitorId, event.ruleId));
          body.append(button);
        } else {
          body.textContent = event.message;
        }
        item.append(time, body);
        return item;
      }),
  );
}

function renderCompetitorSelector(competitors) {
  const inspectable = competitors.filter((competitor) => competitor.ruleInspectorAvailable);
  const current = ui.ruleCompetitor.value;
  if (!inspectable.length) {
    ui.ruleCompetitor.replaceChildren(option("", "No inspectable competitor"));
    ui.ruleCompetitor.disabled = true;
    ui.ruleId.disabled = true;
    return;
  }
  ui.ruleCompetitor.replaceChildren(
    ...inspectable.map((competitor) =>
      option(competitor.id, `${competitor.id} · ${competitor.label} (${competitor.kind})`),
    ),
  );
  ui.ruleCompetitor.value = inspectable.some((competitor) => competitor.id === current)
    ? current
    : inspectable[0].id;
  ui.ruleCompetitor.disabled = false;
  ui.ruleId.disabled = false;
}

function clearInspector(message) {
  const box = document.querySelector("#rule-inspector");
  box.className = "rule-inspector empty-state";
  box.textContent = message;
}

function renderRule(rule) {
  const box = document.querySelector("#rule-inspector");
  box.className = "rule-inspector";
  const heading = document.createElement("strong");
  heading.textContent = `Rule #${rule.id} · ${title(rule.birthReason)}`;
  const condition = document.createElement("div");
  condition.className = "condition-grid";
  condition.replaceChildren(
    ...rule.condition.map((value, index) => {
      const cell = document.createElement("span");
      cell.className = `condition-cell ${value === "*" ? "" : "fixed"}`;
      cell.textContent = value;
      cell.title = `${index < 50 ? `Actor-relative square ${index + 1}` : `Context attribute ${index - 49}`}: ${value}`;
      return cell;
    }),
  );
  const action = document.createElement("p");
  action.append(document.createTextNode("Action "));
  const code = document.createElement("code");
  code.textContent = rule.action;
  action.append(code);
  const metrics = document.createElement("p");
  metrics.textContent = `prediction ${Number(rule.prediction).toFixed(4)} · error ${Number(rule.error).toFixed(4)} · fitness ${Number(rule.fitness).toFixed(4)}`;
  const lineage = document.createElement("p");
  lineage.textContent = `parents ${rule.parentIds?.join(", ") || "none"} · experience ${rule.experience} · numerosity ${rule.numerosity} · action-set size ${Number(rule.actionSetSize).toFixed(2)}`;
  const details = document.createElement("details");
  details.className = "change-history";
  const summary = document.createElement("summary");
  summary.textContent = `Last ${rule.changes?.length || 0} changes`;
  const list = document.createElement("ol");
  if (rule.changes?.length) {
    rule.changes
      .slice()
      .reverse()
      .forEach((change) => {
        const item = document.createElement("li");
        const type = document.createElement("b");
        type.textContent = change.type;
        item.append(type, document.createTextNode(` ${change.detail}`));
        list.append(item);
      });
  } else {
    const item = document.createElement("li");
    item.textContent = "No retained changes.";
    list.append(item);
  }
  details.append(summary, list);
  box.replaceChildren(heading, condition, action, metrics, lineage, details);
}

async function inspectRule(competitorId, ruleId) {
  const id = String(ruleId).trim();
  if (!/^\d+$/.test(id) || id === "0") {
    showToast("Rule ID must be a positive whole number");
    return;
  }
  if (!competitorId) {
    showToast("Choose an inspectable competitor");
    return;
  }
  ui.ruleCompetitor.value = competitorId;
  ui.ruleId.value = id;
  const epoch = state.epoch;
  const runId = state.snapshot?.runId;
  const token = ++state.ruleToken;
  try {
    const rule = await json(
      `/api/arena/runs/current/competitors/${encodeURIComponent(competitorId)}/rules/${encodeURIComponent(id)}`,
    );
    if (
      token !== state.ruleToken ||
      epoch !== state.epoch ||
      runId !== state.snapshot?.runId ||
      competitorId !== ui.ruleCompetitor.value ||
      id !== ui.ruleId.value.trim()
    ) {
      return;
    }
    renderRule(rule);
  } catch (error) {
    if (token === state.ruleToken && epoch === state.epoch && runId === state.snapshot?.runId) {
      showToast(error.message);
    }
  }
}

function selectLiveBoard() {
  state.replayToken += 1;
  clearReplayTimer();
  state.viewMode = "LIVE";
  state.replay = null;
  state.replayGameNumber = null;
  state.replayPly = 0;
  scheduleRender();
}

async function loadReplay(gameNumber) {
  const epoch = state.epoch;
  const runId = state.snapshot?.runId;
  const token = ++state.replayToken;
  clearReplayTimer();
  state.viewMode = "REPLAY";
  state.replay = null;
  state.replayGameNumber = gameNumber;
  state.replayPly = 0;
  scheduleRender();
  try {
    const replay = await json(`/api/arena/runs/current/replays/${encodeURIComponent(gameNumber)}`);
    if (
      token !== state.replayToken ||
      epoch !== state.epoch ||
      runId !== state.snapshot?.runId ||
      state.viewMode !== "REPLAY" ||
      String(state.replayGameNumber) !== String(gameNumber)
    ) {
      return;
    }
    state.replay = replay;
    state.replayPly = 0;
    scheduleRender();
  } catch (error) {
    if (token === state.replayToken && epoch === state.epoch && runId === state.snapshot?.runId) {
      showToast(error.message);
      selectLiveBoard();
    }
  }
}

function competitor(id) {
  return state.snapshot?.competitors?.find((item) => item.id === id);
}

function renderReplayGame() {
  const replay = state.replay;
  if (!replay) {
    setText("game-mode", "REPLAY VAULT");
    setText("game-title", `Loading game ${state.replayGameNumber}…`);
    setText("move-caption", "Loading retained replay.");
    renderBoard(null);
    renderDecision(null, null);
    updateReplayControls();
    return;
  }

  const turn = replay.turns[state.replayPly - 1];
  const board = turn?.boardAfter || replay.initialBoard;
  const white = competitor(replay.whiteCompetitorId);
  const black = competitor(replay.blackCompetitorId);
  setText("game-mode", replay.evaluation ? "FROZEN EVALUATION REPLAY" : "TRAINING REPLAY");
  setText("game-title", `Game ${replay.gameNumber}`);
  setText("white-name", white?.label || `Competitor ${replay.whiteCompetitorId}`);
  setText("white-kind", white?.kind || "—");
  setText("black-name", black?.label || `Competitor ${replay.blackCompetitorId}`);
  setText("black-kind", black?.kind || "—");
  renderBoard(board, turn?.move);
  const caption = state.replayPly
    ? `Ply ${state.replayPly}: ${turn.move.notation || turn.move.actionId}`
    : `Game ${replay.gameNumber}: opening position`;
  setText("move-caption", caption);
  setText("replay-caption", caption);
  renderDecision(turn?.trace || null, turn?.agentKind || null);
  ui.replaySlider.max = replay.turns.length;
  ui.replaySlider.value = state.replayPly;
  updateReplayControls();
}

function setReplayPly(ply) {
  if (!state.replay) return;
  state.replayPly = Math.max(0, Math.min(state.replay.turns.length, ply));
  scheduleRender(false);
}

function updateReplayControls() {
  const available = state.viewMode === "REPLAY" && Boolean(state.replay);
  document.querySelectorAll("#replay-start, #replay-prev, #replay-next, #replay-play").forEach((button) => {
    button.disabled = !available;
  });
  ui.replaySlider.disabled = !available;
  if (!available) {
    ui.replaySlider.max = 0;
    ui.replaySlider.value = 0;
  }
}

function toggleReplayPlayback() {
  if (!state.replay) return;
  if (state.replayTimer) {
    clearReplayTimer();
    return;
  }
  if (state.replayPly >= state.replay.turns.length) state.replayPly = 0;
  ui.replayPlay.textContent = "Pause";
  state.replayTimer = setInterval(() => {
    if (!state.replay || state.replayPly >= state.replay.turns.length) {
      clearReplayTimer();
      return;
    }
    state.replayPly += 1;
    scheduleRender(false);
  }, 350);
}

document.querySelectorAll(".chart-tab").forEach((button) =>
  button.addEventListener("click", () => {
    document.querySelectorAll(".chart-tab").forEach((item) => item.classList.remove("active"));
    button.classList.add("active");
    state.chart = button.dataset.chart;
    renderChart();
  }),
);

ui.start.addEventListener("click", startRun);
ui.pause.addEventListener("click", () => {
  const status = state.snapshot?.status;
  command(status === "PAUSED" || status === "PAUSING" ? "resume" : "pause");
});
ui.stop.addEventListener("click", () => command("stop"));
ui.replayPicker.addEventListener("change", () => {
  if (ui.replayPicker.value === "LIVE") selectLiveBoard();
  else loadReplay(ui.replayPicker.value.slice("REPLAY:".length));
});
ui.replaySlider.addEventListener("input", (event) => setReplayPly(Number(event.target.value)));
document.querySelector("#replay-start").addEventListener("click", () => setReplayPly(0));
document.querySelector("#replay-prev").addEventListener("click", () => setReplayPly(state.replayPly - 1));
document.querySelector("#replay-next").addEventListener("click", () => setReplayPly(state.replayPly + 1));
ui.replayPlay.addEventListener("click", toggleReplayPlayback);
ui.ruleCompetitor.addEventListener("change", () => {
  state.ruleToken += 1;
  clearInspector("Enter a retained rule ID for this competitor.");
  if (ui.ruleId.value) inspectRule(ui.ruleCompetitor.value, ui.ruleId.value);
});
ui.ruleId.addEventListener("change", () => {
  if (ui.ruleId.value) inspectRule(ui.ruleCompetitor.value, ui.ruleId.value);
});
ui.ruleId.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && ui.ruleId.value) {
    event.preventDefault();
    inspectRule(ui.ruleCompetitor.value, ui.ruleId.value);
  }
});
document.querySelector("#learning-metric").addEventListener("change", renderChart);
window.addEventListener("resize", renderChart);

makeBoard();
clearRunUi();
scheduleRender();
loadOptions()
  .then(() => queueSnapshotRefresh())
  .catch((error) => showToast(error.message));
