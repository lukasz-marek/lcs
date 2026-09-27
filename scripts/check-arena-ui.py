"""Exercise an isolated arena server using Firefox's Marionette endpoint.

Requires marionette_driver and Firefox started with --headless --marionette
and a separate temporary profile. See docs/verification.md.
"""
import argparse
import json
import time
from marionette_driver.marionette import Marionette

parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://127.0.0.1:18080')
parser.add_argument('--port', type=int, default=2828)
parser.add_argument('--screenshot')
args = parser.parse_args()
browser = Marionette('localhost', port=args.port)
browser.start_session()
browser.set_window_rect(width=1440, height=1100)
browser.navigate(args.url)


def js(script):
    return browser.execute_script(script, sandbox=None)


def wait(expression, timeout=15):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if js('return Boolean(' + expression + ');'):
            return
        time.sleep(.05)
    raise AssertionError('Timed out: ' + expression)


def click(selector):
    browser.find_element('css selector', selector).click()


def set_value(selector, value):
    js('const element = document.querySelector(' + json.dumps(selector) + ');'
       'element.value = ' + json.dumps(value) + ';'
       'element.dispatchEvent(new Event("change", {bubbles:true}));')


def stop():
    click('#stop')
    wait('document.querySelector("#status-label").textContent === "STOPPED"')
    wait('!document.querySelector("#start").disabled')


try:
    wait('document.querySelector("#agent-a-kind").options.length === 3')
    js('window.arenaErrors = []; window.addEventListener("error", event => window.arenaErrors.push(event.message));'
       'window.addEventListener("unhandledrejection", event => window.arenaErrors.push(String(event.reason)));')
    assert js('return document.querySelectorAll(".square").length') == 100
    assert js('return document.querySelectorAll(".playable").length') == 50
    set_value('#agent-a-kind', 'RANDOM')
    set_value('#agent-b-kind', 'RANDOM')
    set_value('#seed', '00077')
    click('[data-pacing="TURBO"]')
    click('#start')
    wait('parseInt(document.querySelector("#training-count").textContent) >= 3')
    stop()
    print('PASS: start, canonical seed, live updates, stop, restart enabled', flush=True)

    replay = js('return document.querySelector("#replay-picker").options[1].value')
    set_value('#replay-picker', replay)
    wait('!document.querySelector("#replay-next").disabled')
    click('#replay-next')
    wait('document.querySelector("#replay-slider").value === "1"')
    click('#replay-play')
    wait('Number(document.querySelector("#replay-slider").value) >= 2')
    click('#replay-play')
    set_value('#replay-picker', 'LIVE')
    wait('document.querySelector("#replay-next").disabled')
    print('PASS: retained replay loading, stepping, playback, live return', flush=True)

    click('[data-pacing="LIVE"]')
    set_value('#agent-a-kind', 'XCS')
    set_value('#evaluation-interval', '1')
    set_value('#evaluation-games', '2')
    click('#start')
    wait('document.querySelector("#population-size").textContent !== "0 micro rules"')
    click('#pause')
    wait('document.querySelector("#status-label").textContent === "PAUSED"')
    set_value('#rule-id', '1')
    wait('document.querySelector("#rule-inspector strong") !== null')
    assert 'Rule #1' in js('return document.querySelector("#rule-inspector").textContent')
    assert js('return document.querySelectorAll(".playable[aria-label]").length') == 50
    click('[data-chart="learning"]')
    set_value('#learning-metric', 'generality')
    assert js('return !document.querySelector("#learning-metric-label").hidden')
    assert js('return window.arenaErrors') == []
    # Reload is also the reconnect path: the paused run must remain controllable.
    browser.navigate(args.url)
    wait('document.querySelector("#status-label").textContent === "PAUSED"')
    js('window.arenaErrors = []; window.addEventListener("error", event => window.arenaErrors.push(event.message));'
       'window.addEventListener("unhandledrejection", event => window.arenaErrors.push(String(event.reason)));')
    click('[data-pacing="TURBO"]')
    click('#pause')
    wait('document.querySelector("#replay-picker").textContent.includes("evaluation")', 30)
    stop()
    click('[data-chart="learning"]')
    set_value('#learning-metric', 'generality')
    legend = js('return document.querySelector("#chart-legend").textContent')
    assert 'generality' in legend and 'population' not in legend, legend
    click('[data-chart="evaluation"]')
    assert 'A score' in js('return document.querySelector("#chart-legend").textContent')
    print('PASS: XCS telemetry, rule inspection, pause/reload/resume, evaluation, chart units', flush=True)

    set_value('#agent-a-kind', 'MCTS')
    set_value('#agent-a-preset', 'FAST')
    set_value('#agent-b-kind', 'RANDOM')
    click('[data-pacing="LIVE"]')
    click('#start')
    wait('document.querySelector("#decision-method").textContent === "MCTS"', 30)
    click('#pause')
    wait('document.querySelector("#status-label").textContent === "PAUSED"')
    assert js('return document.querySelectorAll(".decision-bar.selected").length') == 1
    assert js('return document.querySelector(".decision-bar.selected .bar-fill").getBoundingClientRect().width') > 0
    stop()
    print('PASS: MCTS preset, decision evidence and chosen action', flush=True)

    for width in (1440, 768, 390):
        browser.set_window_rect(width=width, height=1100)
        wait('document.documentElement.scrollWidth <= window.innerWidth')
    if args.screenshot:
        with open(args.screenshot, 'wb') as file:
            file.write(browser.screenshot(full=True, format='binary'))
    assert js('return window.arenaErrors') == []
    print('PASS: desktop, tablet and mobile layout', flush=True)
finally:
    # Do not leave a learning run consuming resources if an assertion failed.
    js('if (!document.querySelector("#stop").disabled) document.querySelector("#stop").click();')
    browser.delete_session()
