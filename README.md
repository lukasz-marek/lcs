# LCS spectator arena

A local browser arena for watching categorical XCS learn 10×10 international
draughts against UCT search and random baselines.

Requires Java 25. Run it with:

```shell
./gradlew :app:bootRun
```

Open <http://127.0.0.1:8080>. Runs and learned populations live only in memory.

```shell
./gradlew :app:test :app:spotlessCheck
```

See [the implemented design](docs/lcs-design.md) for the rules, agent lifecycle,
XCS and UCT details, arena API, validation scope, and research references.

See [the verification report](docs/verification.md) for audit findings, remaining
limits, and the repeatable Firefox GUI check.
