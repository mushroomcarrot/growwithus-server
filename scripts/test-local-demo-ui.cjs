"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { setImmediate: nextTurn } = require("node:timers/promises");
const root = path.join(__dirname, "../src/main/resources/local-demo");
const reply = (data, status = 200) => ({ ok: status < 400, status, json: async () => ({ data, error: status >= 400 ? { code: "fixture", message: "fixture failure" } : null }) });
const today = { weatherNotice: "fixture weather", gardens: [{ gardenId: "gdn_1", plotCode: "B14", tasks: [{ taskId: "tsk_1", title: "fixture", description: "흙과 잎을 살펴 주세요.", status: "TODO", minutes: 5 }] }] };
const ready = { voiceMemoId: "vm_1", transcript: "잎에 점이 보여요", summary: "잎에 점이 보여요.", issues: ["LEAF_SPOT"], status: "DRAFT", processingStatus: "READY", retryable: false };

function harness(respond) {
  const elements = new Map();
  for (const match of fs.readFileSync(path.join(root, "index.html"), "utf8").matchAll(/id="([^"]+)"/g)) elements.set(match[1], element());
  function element() { return { value: "", textContent: "", files: [], listeners: {}, children: [],
    addEventListener(type, fn) { this.listeners[type] = fn; },
    append(...items) { this.children.push(...items); }, replaceChildren(...items) { this.children = items; }, removeAttribute() {} }; }
  const calls = [], spoken = [];
  const window = { addEventListener() {}, speechSynthesis: { cancel() {}, speak(item) { spoken.push(item); } }, SpeechSynthesisUtterance: class { constructor(text) { this.text = text; } } };
  const navigator = { mediaDevices: {} };
  class Recorder { static isTypeSupported() { return true; } }
  window.MediaRecorder = Recorder;
  vm.runInNewContext(fs.readFileSync(path.join(root, "app.js"), "utf8"), {
    document: { getElementById: id => { assert.ok(elements.has(id), "Missing HTML element: " + id); return elements.get(id); }, querySelectorAll: () => [], createElement: element },
    window, navigator, MediaRecorder: Recorder, File, FormData, AbortSignal, setTimeout, clearTimeout,
    URL: { createObjectURL: () => "blob:fixture", revokeObjectURL() {} },
    Option: class { constructor(label, value) { this.label = label; this.value = value; } },
    fetch: async (url, options) => {
      calls.push({ url, options });
      if (url === "/auth/login") return reply({ accessToken: "old-token", refreshToken: "old-refresh" });
      return respond(url, options, calls);
    }
  });
  return { elements, calls, spoken, navigator,
    click: id => elements.get(id).listeners.click(),
    change: id => elements.get(id).listeners.change(),
    async login() { await this.click("loginCare"); },
    audio() { elements.get("audioFile").files = [new File(["fixture"], "recording.webm", { type: "audio/webm" })]; this.change("audioFile"); } };
}

test("concurrent expired requests share one refresh and retry with the new token", async () => {
  let refresh, refreshCount = 0, initial = true;
  const app = harness(async (url, options) => {
    if (url === "/auth/refresh") { refreshCount++; return new Promise(resolve => { refresh = () => resolve(reply({ accessToken: "new-token", refreshToken: "new-refresh" })); }); }
    if (initial) return reply(today);
    if (options.headers.Authorization === "Bearer old-token") return reply(null, 401);
    return reply(url.endsWith("/latest") ? ready : today);
  });
  await app.login(); initial = false;
  const requests = [app.click("loadTasks"), app.click("recover")];
  await nextTurn(); assert.equal(refreshCount, 1); refresh(); await Promise.all(requests);
  assert.equal(refreshCount, 1);
  assert.equal(app.calls.filter(c => c.options.headers.Authorization === "Bearer new-token").length, 2);
});

test("network failure never automatically repeats an audio upload", async () => {
  let uploads = 0;
  const app = harness(async url => { if (url.endsWith("/voice-memos")) { uploads++; throw new Error("network disconnected"); } return reply(today); });
  await app.login(); app.audio(); await app.click("sendAudio");
  assert.equal(uploads, 1); assert.equal(app.calls.filter(c => c.url === "/auth/refresh").length, 0);
  assert.match(app.elements.get("notice").textContent, /network disconnected/);
});

test("a second 401 stops after one refresh instead of looping", async () => {
  let initial = true;
  const app = harness(async url => url === "/auth/refresh" ? reply({ accessToken: "new-token", refreshToken: "new-refresh" }) : initial ? reply(today) : reply(null, 401));
  await app.login(); initial = false; await app.click("loadTasks");
  assert.equal(app.calls.filter(c => c.url === "/auth/refresh").length, 1);
  assert.equal(app.calls.filter(c => c.url === "/caretaker/tasks/today").length, 3);
  assert.match(app.elements.get("notice").textContent, /fixture failure/);
});

test("403 permission denial never attempts token refresh", async () => {
  let initial = true;
  const app = harness(async () => initial ? reply(today) : reply(null, 403));
  await app.login(); initial = false; await app.click("loadTasks");
  assert.equal(app.calls.filter(c => c.url === "/auth/refresh").length, 0);
});

test("selected audio cannot silently be omitted from task completion", async () => {
  const app = harness(async () => reply(today)); await app.login(); app.audio(); await app.click("complete");
  assert.equal(app.calls.filter(c => c.url.endsWith("/complete")).length, 0);
  assert.match(app.elements.get("notice").textContent, /음성을 먼저/);
});

test("changing tasks while microphone permission is pending releases the microphone", async () => {
  const app = harness(async () => reply(today)); await app.login();
  let allow, stopped = 0;
  app.navigator.mediaDevices.getUserMedia = () => new Promise(resolve => { allow = resolve; });
  const pending = app.click("record"); app.change("task");
  allow({ getTracks: () => [{ stop() { stopped++; } }] }); await pending;
  assert.equal(stopped, 1); assert.match(app.elements.get("notice").textContent, /작업이 바뀌었어요/);
});

test("Korean issue labels and browser speech use the actual memo summary", async () => {
  const app = harness(async url => reply(url.endsWith("/voice-memos") ? structuredClone(ready) : today));
  await app.login(); app.audio(); await app.click("sendAudio"); await app.click("readSummary");
  assert.match(app.elements.get("issues").textContent, /잎에 점이 있어요/);
  assert.equal(app.spoken[0].text, ready.summary); assert.equal(app.spoken[0].lang, "ko-KR");
});

test("editing the summary before confirmation clears stale automatic issue labels", async () => {
  const app = harness(async url => reply(url.endsWith("/voice-memos") ? structuredClone(ready) : url.endsWith("/confirm") ? { status: "CONFIRMED" } : today));
  await app.login(); app.audio(); await app.click("sendAudio");
  app.elements.get("summary").value = "다시 보니 잎이 건강해요."; await app.click("confirm");
  assert.doesNotMatch(app.elements.get("issues").textContent, /잎에 점이 있어요/);
  assert.match(app.elements.get("memoState").textContent, /확정했어요/);
});

function familyResponse(url) {
  if (url.endsWith("/growth")) return reply({ stage: "FLOWER", progress: .4, explanation: "fixture" });
  if (url.endsWith("/harvest")) return reply({ expectedDate: null, message: "fixture" });
  if (url.endsWith("/diary")) return reply({ items: [], nextCursor: null });
  if (url.endsWith("/visit-slots")) return reply([
    { slotId: "slot_20261128_10", date: "2026-11-28", startTime: "10:00", endTime: "11:00", available: true },
    { slotId: "slot_20261128_11", date: "2026-11-28", startTime: "11:00", endTime: "12:00", available: false }
  ]);
  if (url === "/visits") return reply({ visitId: "vst_1", date: "2026-11-28", startTime: "10:00", status: "RESERVED" });
  return reply(null);
}

test("family visit uses the selected slot and cancels only the created reservation", async () => {
  const app = harness(async url => familyResponse(url));
  await app.click("loginOwner"); await app.click("reserveVisit");
  assert.equal(app.calls.filter(c => c.url === "/visits").length, 0);
  await app.click("loadSlots"); assert.equal(app.elements.get("slot").children[2].disabled, true);
  app.elements.get("slot").value = "slot_20261128_10"; await app.click("reserveVisit");
  const request = app.calls.find(c => c.url === "/visits");
  assert.deepEqual(JSON.parse(request.options.body), { gardenId: "gdn_1", slotId: "slot_20261128_10", headcount: 1, memo: null });
  assert.match(app.elements.get("visitState").textContent, /vst_1/);
  await app.click("cancelVisit"); await app.click("cancelVisit");
  assert.equal(app.calls.filter(c => c.url === "/visits/vst_1").length, 1);
});

test("a lost cancellation response preserves the reservation id for manual recovery", async () => {
  let cancellations = 0;
  const app = harness(async url => {
    if (url === "/visits/vst_1") {
      if (++cancellations === 1) throw new Error("network disconnected");
      return { ok: false, status: 409, json: async () => ({ data: null, error: { code: "C4095", message: "이미 취소됐어요" } }) };
    }
    return familyResponse(url);
  });
  await app.click("loginOwner"); app.elements.get("slot").value = "slot_20261128_10"; await app.click("reserveVisit");
  await app.click("cancelVisit"); assert.equal(cancellations, 1);
  await app.click("cancelVisit"); assert.equal(cancellations, 2);
  assert.match(app.elements.get("visitState").textContent, /취소했어요/);
});
