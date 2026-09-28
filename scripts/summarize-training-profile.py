#!/usr/bin/env python3
"""Summarize `jfr print --json` output for the training benchmark."""
import collections
from datetime import datetime
import json
import re
import sys

with open(sys.argv[1]) as source:
    events = json.load(source)['recording']['events']
starts = [datetime.fromisoformat(e['values']['startTime']) for e in events
          if e['type'] == 'lcs.BenchmarkAgentCall' and e['values']['operation'] == 'beginEpisode']
start = min(starts) if starts else None
samples = collections.Counter()
threads = collections.Counter()
durations = collections.defaultdict(list)
cpu = collections.defaultdict(list)
for event in events:
    value = event['values']
    if start and datetime.fromisoformat(value['startTime']) < start:
        continue
    if event['type'] == 'lcs.BenchmarkAgentCall':
        duration = value['duration']
        match = re.fullmatch(r'PT([\d.]+)S', duration)
        if match:
            durations[value['operation']].append(float(match[1]) * 1000)
    elif event['type'] == 'jdk.ThreadCPULoad':
        thread = value.get('eventThread') or {}
        cpu[thread.get('javaName', '?')].append(value['user'] + value['system'])
    elif event['type'] == 'jdk.ExecutionSample':
        thread = value.get('sampledThread') or {}
        threads[thread.get('javaName', '?')] += 1
        frames = (value.get('stackTrace') or {}).get('frames', [])
        names = [f['method']['type']['name'] + '.' + f['method']['name'] for f in frames]
        stack = ' '.join(names)
        category = 'other / runtime'
        # Attribute to the innermost measured phase, with publication before its callers.
        for label, markers in [
            ('publication', ['ArenaRun.publish', 'ArenaRun.buildSnapshot', 'ArenaEventStream.']),
            ('telemetry / history', ['ArenaRun.captureTelemetry', 'ArenaRun.collectEvolution', 'ArenaHistory.', 'XcsPopulation.telemetry']),
            ('learning update', ['XcsPopulation.update', 'XcsPopulation.lambda$update', 'XcsPopulation.retainRuleChange']),
            ('genetics / deletion', ['XcsPopulation.runGeneticAlgorithm', 'XcsPopulation.delete', 'XcsPopulation.lambda$delete', 'XcsPopulation.isDeletable']),
            ('matching / covering', ['XcsPopulation.match', 'XcsPopulation.lambda$match', 'MatchingExecutor.range', 'XcsPopulation.reserveCoveringSpace', 'XcsPopulation.lambda$reserveCoveringSpace']),
            ('game rules', ['lmarek/lcs/draughts/']),
        ]:
            if any(marker in stack for marker in markers):
                category = label
                break
        samples[category] += 1
print('CPU execution samples:', dict(samples))
print('Sampled threads:', dict(threads))
for operation, values in sorted(durations.items()):
    values.sort()
    print(f'{operation}: n={len(values)} mean={sum(values)/len(values):.3f}ms '
          f'p50={values[len(values)//2]:.3f}ms p95={values[min(len(values)-1,int(len(values)*.95))]:.3f}ms')
for thread, values in sorted(cpu.items()):
    if 'xcs-worker' in thread or 'trainingGame' in thread:
        print(f'{thread}: mean sampled CPU load={sum(values)/len(values):.4f}')
