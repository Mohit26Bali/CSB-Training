// A tiny driver for a real browser, used by the check:* scripts. No extra library: it starts headless Microsoft Edge
// or Chrome and talks to it over the Chrome DevTools Protocol (Node 22 has WebSocket built in).
// Temporary: phase F6 replaces these scripts with Playwright (D26).
//
// Settings (environment variables):
//   APP_URL=http://localhost:5173     where the frontend runs
//   API_URL=http://localhost:8080     the gateway (only for the scripts that also call the API directly)
//   BROWSER_PATH=<msedge.exe or chrome.exe>   if it is not in a standard place
//   SHOTS=<folder>                    save a screenshot at each shot() call
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export const APP_URL = process.env.APP_URL || 'http://localhost:5173';
export const API_URL = process.env.API_URL || 'http://localhost:8080';
export const DEMO_PASSWORD = 'Icc@Demo2026';
const SHOTS = process.env.SHOTS || '';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
export { sleep };

/** Calls the backend directly (no browser), for preparing data or checking what the server says. */
export async function api(method, path, { token, body } = {}) {
  const res = await fetch(API_URL + path, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }
  return { status: res.status, body: json };
}
export async function apiLogin(username) {
  const r = await api('POST', '/api/auth/login', { body: { username, password: DEMO_PASSWORD } });
  if (r.status !== 200) throw new Error(`API login failed for ${username}: ${r.status}`);
  return r.body.token;
}

export async function launchBrowser({ width = 1280, height = 860 } = {}) {
  const candidates = [
    process.env.BROWSER_PATH,
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  ].filter(Boolean);
  const exe = candidates.find((p) => existsSync(p));
  if (!exe) { console.error('No Edge or Chrome found. Set BROWSER_PATH.'); process.exit(2); }

  const port = 9300 + Math.floor(Math.random() * 500);
  const profile = mkdtempSync(join(tmpdir(), 'icc-browser-'));
  const proc = spawn(exe, ['--headless=new', '--disable-gpu', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, `--window-size=${width},${height}`, '--no-first-run', 'about:blank'], { stdio: 'ignore' });

  let targets;
  for (let i = 0; i < 60 && !targets; i++) { try { targets = await (await fetch(`http://localhost:${port}/json`)).json(); } catch { await sleep(250); } }
  if (!targets) throw new Error('The browser did not start.');
  const ws = new WebSocket(targets.find((t) => t.type === 'page').webSocketDebuggerUrl);
  await new Promise((r) => (ws.onopen = r));
  let nextId = 1;
  const waiting = new Map();
  ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && waiting.has(d.id)) { waiting.get(d.id)(d); waiting.delete(d.id); } };
  const send = (method, params = {}) => new Promise((res) => { const id = nextId++; waiting.set(id, res); ws.send(JSON.stringify({ id, method, params })); });
  await send('Page.enable');
  const viewport = (w, h, mobile = false) => send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: mobile ? 2 : 1, mobile });
  await viewport(width, height);

  const js = async (expr) => (await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true })).result?.result?.value;
  const q = JSON.stringify;

  const b = {
    js,
    sleep,
    viewport,
    async waitFor(expr, label, ms = 8000) {
      const end = Date.now() + ms;
      while (Date.now() < end) { if (await js(expr)) return true; await sleep(120); }
      throw new Error('timeout waiting for: ' + label);
    },
    async go(path) {
      await send('Page.navigate', { url: APP_URL + path });
      await sleep(250);
      await b.waitFor('document.readyState==="complete" && !!document.getElementById("root")?.children.length', 'page ' + path);
    },
    path: () => js('location.pathname'),
    text: () => js('document.body.innerText'),
    /**
     * Types into a MUI text field found by the start of its label (works for single and multi line fields).
     * Waits up to 4 seconds for the field to exist and throws if it never does, so a missed typing cannot go unnoticed.
     */
    async setField(label, value) {
      const expr = `(() => {
        const root=[...document.querySelectorAll('.MuiFormControl-root')].find(f=>(f.querySelector('label')?.textContent||'').startsWith(${q(label)}));
        if(!root) return false;
        const el=[...root.querySelectorAll('input,textarea')].find(e=>e.getAttribute('aria-hidden')!=='true' && e.type!=='hidden');
        if(!el) return false;
        const proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;
        Object.getOwnPropertyDescriptor(proto,'value').set.call(el,${q(value)});
        el.dispatchEvent(new Event('input',{bubbles:true}));
        return true; })()`;
      for (let i = 0; i < 33; i++) { if (await js(expr)) return true; await sleep(120); }
      throw new Error('field not found: ' + label);
    },
    /** Chooses an option in a MUI select (TextField select): finds the select by its accessible label (aria-labelledby), then the option by its text. */
    async selectOption(label, optionText) {
      const opened = await js(`(() => {
        const box=[...document.querySelectorAll('[role=combobox]')].find(c=>{const l=document.getElementById(c.getAttribute('aria-labelledby')||''); return l && l.textContent.startsWith(${q(label)});});
        if(!box) return false;
        box.dispatchEvent(new MouseEvent('mousedown',{bubbles:true,button:0})); return true; })()`);
      if (!opened) throw new Error('select not found: ' + label);
      await b.waitFor(`!!document.querySelector('[role=listbox]')`, 'options of ' + label);
      const clicked = await js(`(() => { const li=[...document.querySelectorAll('[role=listbox] [role=option]')].find(o=>o.textContent.trim().startsWith(${q(optionText)})); if(!li) return false; li.click(); return true; })()`);
      if (!clicked) throw new Error(`option not found: ${optionText} in ${label}`);
      await b.waitFor(`!document.querySelector('[role=listbox]')`, 'options closed');
    },
    /** Types into a searchable picker (MUI Autocomplete) and clicks the option that contains optionText. */
    async pickOption(label, typed, optionText) {
      if (!(await b.setField(label, typed))) throw new Error('picker not found: ' + label);
      await b.waitFor(`[...document.querySelectorAll('[role=listbox] [role=option]')].some(o=>o.textContent.includes(${q(optionText)}))`, 'option ' + optionText, 10000);
      await js(`(() => { const o=[...document.querySelectorAll('[role=listbox] [role=option]')].find(o=>o.textContent.includes(${q(optionText)})); o.click(); })()`);
      await sleep(250);
    },
    /** Clicks a button or link by its exact text. Waits up to 3 seconds for it to exist and be enabled, like a person would. */
    async clickButton(text) {
      const expr = `(() => { const x=[...document.querySelectorAll('button, a')].find(e=>e.textContent.trim()===${q(text)} && !e.disabled && e.getAttribute('aria-disabled')!=='true'); if(!x) return false; x.click(); return true; })()`;
      for (let i = 0; i < 25; i++) { if (await js(expr)) return true; await sleep(120); }
      return false;
    },
    /** Same, but only inside the open dialog (the page behind it may have a button with the same text). */
    async clickDialogButton(text) {
      const expr = `(() => { const d=document.querySelector('[role=dialog]'); const x=[...(d?d.querySelectorAll('button'):[])].find(e=>e.textContent.trim()===${q(text)} && !e.disabled); if(!x) return false; x.click(); return true; })()`;
      for (let i = 0; i < 25; i++) { if (await js(expr)) return true; await sleep(120); }
      throw new Error('no enabled button "' + text + '" in the dialog');
    },
    hasButton: (text) => js(`[...document.querySelectorAll('button')].some(e=>e.textContent.trim()===${q(text)})`),
    chips: () => js(`[...document.querySelectorAll('.MuiChip-root')].map(c=>c.textContent)`),
    menu: () => js(`[...document.querySelectorAll('[role=navigation] a')].map(a=>a.textContent.replace(/soon$/,'').trim())`),
    async login(user, password = DEMO_PASSWORD) {
      await b.waitFor(`!!document.querySelector('input[autocomplete=username]')`, 'login form');
      await b.setField('Email', user);
      await b.setField('Password', password);
      await b.waitFor(`(()=>{const x=[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='Log in');return x&&!x.disabled})()`, 'login button');
      await b.clickButton('Log in');
    },
    /** Logs in a user from a clean state and waits for Home. */
    async loginAs(user) {
      await b.go('/login');
      await b.login(user);
      await b.waitFor('location.pathname==="/" && document.body.innerText.includes("Welcome")', 'home after login');
    },
    async logout() {
      await b.clickButton('Log out');
      await b.waitFor('location.pathname==="/login"', 'login page after logout');
    },
    async shot(name) {
      if (!SHOTS) return;
      mkdirSync(SHOTS, { recursive: true });
      const r = await send('Page.captureScreenshot', { format: 'png' });
      writeFileSync(join(SHOTS, `${name}.png`), Buffer.from(r.result.data, 'base64'));
    },
    results: [],
    check(name, ok, detail = '') {
      b.results.push(!!ok);
      console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`);
    },
    async finish() {
      const passed = b.results.filter(Boolean).length;
      console.log(`\n${passed} of ${b.results.length} checks passed`);
      try { await send('Browser.close'); } catch { /* already closed */ }
      proc.kill();
      return passed === b.results.length;
    },
    async fail(e) {
      console.log('FAIL  script error: ' + e.message);
      b.results.push(false);
      try { await b.shot('error'); } catch { /* ignore */ }
    },
  };
  return b;
}
