"""Check history races and profile the real UI in an isolated headless Firefox.

Start Firefox with --headless --no-remote --profile /tmp/lcs-history-firefox
--marionette about:blank. Requires marionette_driver; no arena server is needed.
"""
import argparse
import json
from pathlib import Path
from marionette_driver.marionette import Marionette

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=2828)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1] / 'app/src/main/resources/static'
browser = Marionette('localhost', port=args.port)
browser.start_session()
browser.set_window_rect(width=1440, height=1100)
try:
    browser.navigate(root.joinpath('index.html').as_uri())
    browser.execute_script('const style=document.createElement("style");'
                           'style.textContent=arguments[0]; document.head.append(style);',
                           script_args=[root.joinpath('arena.css').read_text()], sandbox=None)
    source = root.joinpath('arena.js').read_text().split('makeBoard();\nclearRunUi();')[0]
    browser.execute_script(source + '''
      makeBoard(); clearRunUi();
      const requests = [];
      window.fetch = (url) => new Promise((resolve, reject) => requests.push({url, resolve, reject}));
      let chartDraws = 0;
      const originalChart = renderChart;
      renderChart = () => { chartDraws++; originalChart(); };
      window.historyTest = { state, ui, requests, acceptSnapshot, render, renderChart,
        setReplayPly, resetForRun, acceptHistory, queueHistoryRefresh,
        draws: () => chartDraws };
    ''', sandbox=None)
    result = browser.execute_async_script('''
      const done = arguments[arguments.length - 1];
      (async () => {
        const h = window.historyTest;
        const assert = (value, message) => { if (!value) throw new Error(message); };
        const settle = () => new Promise(resolve => setTimeout(resolve, 40));
        const snapshot = (runId, revision, charts = 0, replays = 0, evolution = 0) => ({
          runId, revision, status: "STOPPED", trainingGames: 0, statistics: {},
          competitors: [], historyRevisions: {charts, replays, evolution}
        });
        const finish = (request, body) => request.resolve({ok: true, text: async () => JSON.stringify(body)});
        h.acceptSnapshot(snapshot("a", 1));
        assert(h.requests.length === 1, "initial history fetch");
        h.acceptSnapshot(snapshot("a", 2, 1));
        assert(h.requests.length === 1, "coalesce history requests");
        finish(h.requests.shift(), {...snapshot("a", 1), charts: {}, recentReplays: [], evolutionHighlights: []});
        await settle();
        assert(h.requests.length === 1, "fetch newer revision after pending response");
        assert(h.requests[0].url.includes("charts=0"), "send cached revisions");
        finish(h.requests.shift(), {...snapshot("a", 2, 1), charts: {results: {"A wins": [{x:1,value:1}]}}});
        await settle();
        const charts = h.state.history.charts;
        const draws = h.draws();
        h.acceptSnapshot(snapshot("a", 3, 1));
        await settle();
        assert(h.draws() === draws && !h.requests.length, "board update reuses history and chart");
        h.acceptHistory({...snapshot("a", 1), charts: {}});
        assert(h.state.history.charts === charts, "reject older history");

        h.acceptSnapshot(snapshot("a", 4, 2));
        const oldRequest = h.requests.shift();
        h.acceptSnapshot({...snapshot("b", 1), charts: {}, recentReplays: [], evolutionHighlights: []});
        finish(oldRequest, {...snapshot("a", 4, 2), charts: {results:{stale:[]}}});
        await settle();
        assert(h.state.snapshot.runId === "b" && !h.state.history.charts.results, "ignore old run response");

        h.acceptSnapshot(snapshot("b", 2, 1));
        h.requests.shift().reject(new Error("offline"));
        await settle();
        assert(h.state.historyRevisions.charts === 0, "retain cache after failure");
        await new Promise(resolve => setTimeout(resolve, 1100));
        assert(h.requests.length === 1, "retry after terminal snapshot without SSE");
        finish(h.requests.shift(), {...snapshot("b", 2, 1), charts: {}});
        await settle();
        assert(h.state.historyRevisions.charts === 1, "retry recovers history");

        const replay = {gameNumber:1, result:"Draw", length:1};
        h.acceptSnapshot({...snapshot("b", 3, 1, 1, 1), recentReplays:[replay],
          evolutionHighlights:[{gameNumber:1, message:"event"}]});
        h.state.viewMode = "REPLAY"; h.state.replayGameNumber = 1;
        h.state.replay = {gameNumber:1, initialBoard:{}, turns:[{boardAfter:{}, move:{notation:"1-2"}}]};
        h.render();
        const option = h.ui.replayPicker.options[1];
        const event = document.querySelector("#evolution-feed").firstChild;
        const replayDraws = h.draws();
        h.setReplayPly(1);
        await settle();
        assert(h.ui.replaySlider.value === "1", "replay step renders controls");
        assert(h.ui.replayPicker.options[1] === option &&
          document.querySelector("#evolution-feed").firstChild === event && h.draws() === replayDraws,
          "replay step leaves history DOM and chart untouched");
        h.acceptSnapshot({...snapshot("b", 4, 1, 2, 1), recentReplays:[]});
        await settle();
        assert(h.ui.replayPicker.value === "REPLAY:1" && !h.ui.replaySlider.disabled,
          "loaded replay survives retention eviction");

        h.resetForRun();
        const timings=[];
        const measure = (fn) => {
          for(let i=0;i<20;i++) fn();
          const start=performance.now();
          for(let i=0;i<100;i++) fn();
          return (performance.now()-start)/100;
        };
        for(const count of [100,1000,2048]) {
          const values = n => Array.from({length:count},(_,i)=>({x:i+1,value:(Math.sin(i/30)+1)*n+i/10}));
          const charts={results:{"A wins":values(1),"B wins":values(2),Draws:values(3)},
            learning:{},evaluation:{"A score":values(.4),"B score":values(.6)}};
          for(const side of ["A","B"])for(const metric of ["population","generality","error","covering"])
            charts.learning[`${side} ${metric}`]=values(7);
          const full={...snapshot("profile",count,count,count,count),trainingGames:count,charts,
            recentReplays:Array.from({length:20},(_,i)=>({gameNumber:i+1,result:"Draw",length:100})),
            evolutionHighlights:Array.from({length:200},(_,i)=>({gameNumber:i,ruleId:i+1,competitorId:"A",message:"Covered rule"}))};
          h.acceptSnapshot(full); h.render();
          const {charts: omittedCharts,recentReplays,evolutionHighlights,...live}=full;
          const fullJson=JSON.stringify(full), liveJson=JSON.stringify(live);
          timings.push({points:count,fullBytes:new TextEncoder().encode(fullJson).length,
            liveBytes:new TextEncoder().encode(liveJson).length,
            fullParseMs:measure(()=>JSON.parse(fullJson)),liveParseMs:measure(()=>JSON.parse(liveJson)),
            unchangedRenderMs:measure(()=>{h.render();document.body.getBoundingClientRect();})});
        }
        h.resetForRun();
        return {checks:"PASS: incremental requests, coalescing, stale responses, restart, retry, replay retention and rendering",timings};
      })().then(done, error => done({error: error.stack}));
    ''', sandbox=None)
    if 'error' in result:
        raise AssertionError(result['error'])
    print(json.dumps(result, indent=2))
finally:
    browser.delete_session()
