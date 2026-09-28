# XCS Rule Set Persistence Specification

## Goal

Allow a user to give an XCS population a stable name, train it over multiple arena runs, stop and restart training, and choose a different opponent without losing the learned population. Rule sets must survive application restarts.

## User workflow

1. Choose XCS for a competitor and enter a rule set name, or choose an existing saved rule set.
2. Start an arena against any supported opponent. The selected XCS agent resumes the saved population; a new name creates a population from the selected XCS settings.
3. Training updates are saved automatically as games complete. The Stop control ends the arena while retaining the population.
4. Change the other competitor and start another run with the same rule set name to continue training it.
5. A rule set can be selected from either competitor slot, but only one active arena can write to a given set at a time.

## Functional requirements

- Provide an API to list saved rule sets and expose enough metadata for selection (name, update time, rule count, and training iteration count).
- Accept an optional rule set name on each competitor configuration. It is valid only for XCS agents.
- On run start, load the named XCS population if it exists. Otherwise create it with the submitted XCS parameters. Existing saved parameters are authoritative on resume so a user cannot accidentally reinterpret stored rules with incompatible XCS parameters.
- Persist the complete learning state needed for correct continued XCS updates: classifiers (condition, action, prediction, prediction error, fitness, experience, numerosity, action-set size, GA timestamp, lineage and birth reason), population counters and IDs, retained evolution/rule-change history, and format version.
- Save after each completed training game and on normal stop. Writes must be atomic so a crash cannot leave a truncated active rule set. Do not save frozen evaluation copies.
- Allow the arena run/history to be new on resume; persistence is for the named XCS population, not old replay/history charts.
- Support naming with a bounded, safe character set and reject traversal paths or blank names.
- Include a UI field that is shown only for XCS, supports choosing an existing set or entering a new name, and explain that the set is updated automatically.

## Non-goals

- Persist arena replays, score charts, current game state, pacing, or opponent configuration.
- Guarantee identical random decisions after restart. A resumed population continues learning from its saved classifiers and counters using the new run seed.
- Merge two populations or rename/delete stored populations in this change.

## Persistence and failure behavior

- Store rule-set files under a configurable application data directory, defaulting to a local `data/xcs-rule-sets` directory.
- Read/list malformed or unsupported files as errors with a useful message; never silently start a blank population when a requested name exists but cannot be loaded.
- If an autosave fails, fail the arena run visibly rather than continue while claiming persistence is active.
- A rule set may be written only by its owning active arena run; names cannot be loaded into a second concurrent run while the first is active.

## Acceptance checks

- A named XCS set can be created, trained, stopped, and listed with updated counters.
- Restarting the application and selecting that name restores its learned classifier population and counters.
- Resuming under a changed opponent adds learning to the same rule population.
- Existing non-XCS runs and unnamed XCS runs retain current behavior.
- Invalid names and corrupt files produce actionable client errors; resuming a saved name keeps its original XCS parameters.
