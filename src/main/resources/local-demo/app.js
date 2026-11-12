"use strict";
(() => {
  const $ = id => document.getElementById(id);
  const tokens = {};
  const issueLabels = { LEAF_SPOT: "잎에 점이 있어요", YELLOW_LEAF: "잎이 노랗게 보여요", WILTING: "작물이 시들어 보여요", PEST_SUSPECTED: "벌레가 보인다는 기록이 있어요", STEM_DAMAGE: "줄기가 손상된 것 같아요" };
  let tasks = [], memo = null, photoId = null, audio = null, playbackUrl = null;
  let recorder = null, stream = null, diaryCursor = null, selectionVersion = 0;
  let activeGarden = "gdn_1";
  const tell = text => { $("notice").textContent = text; };
  const taskId = () => { if (!$("task").value) throw new Error("진행할 작업을 골라 주세요."); return $("task").value; };
  async function refreshSession(actor, session) {
    if (!session.refreshing) session.refreshing = (async () => {
      const response = await fetch("/auth/refresh", { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: session.refreshToken }), signal: AbortSignal.timeout(15000) });
      const result = await response.json();
      if (!response.ok || result.error?.code || !result.data?.accessToken || !result.data?.refreshToken) throw new Error("연결이 만료됐어요. 해당 계정을 다시 연결해 주세요.");
      if (tokens[actor] === session) Object.assign(session, result.data);
    })().finally(() => { delete session.refreshing; });
    await session.refreshing;
  }
  async function api(actor, path, options = {}, retried = false) {
    if (!tokens[actor] && path !== "/auth/login") throw new Error("먼저 해당 계정을 연결해 주세요.");
    const session = tokens[actor];
    const headers = { ...(options.headers || {}) };
    if (session && path !== "/auth/login") headers.Authorization = "Bearer " + session.accessToken;
    let body = options.body;
    if (body !== undefined && !(body instanceof FormData)) { body = JSON.stringify(body); headers["Content-Type"] = "application/json"; }
    const response = await fetch(path, { ...options, headers, body, cache: "no-store", signal: AbortSignal.timeout(190000) });
    if (response.status === 401 && !retried && session?.refreshToken && path !== "/auth/login") {
      // 같은 계정의 동시 401은 토큰을 한 번만 갱신한다. 네트워크 오류로 쓰기 요청을 다시 보내지는 않는다.
      if (tokens[actor] === session && headers.Authorization === "Bearer " + session.accessToken) await refreshSession(actor, session);
      return api(actor, path, options, true);
    }
    const result = await response.json();
    if (!response.ok || result.error?.code) throw new Error(result.error?.message || ("요청 실패: " + response.status));
    return result.data;
  }
  function action(id, fn) {
    $(id).addEventListener("click", async () => {
      const button = $(id); button.disabled = true;
      try { await fn(); } catch (error) { tell(error.name === "TimeoutError" ? "응답을 받지 못했어요. 마지막 메모 복구 조회를 눌러 주세요." : error.message); }
      finally { button.disabled = false; }
    });
  }
  async function login(actor, username) {
    const result = await api(actor, "/auth/login", { method: "POST", body: { username, password: $("password").value } });
    tokens[actor] = { accessToken: result.accessToken, refreshToken: result.refreshToken };
    $("sessions").textContent = "연결됨: " + Object.keys(tokens).join(", ");
    tell("계정이 연결됐어요.");
  }
  function clearTask() {
    selectionVersion++;
    if (recorder?.state === "recording") recorder.stop();
    memo = null; photoId = null;
    audio = null;
    $("audioFile").disabled = false; $("stopRecord").disabled = true; $("recordStatus").textContent = "";
    if (playbackUrl) { URL.revokeObjectURL(playbackUrl); playbackUrl = null; }
    $("playback").removeAttribute("src"); $("audioFile").value = ""; $("photo").value = "";
    $("transcript").value = ""; $("summary").value = ""; $("memoState").textContent = "";
    $("issues").textContent = "";
    window.speechSynthesis?.cancel();
    const task = tasks.find(t => t.taskId === $("task").value);
    $("taskDescription").textContent = task?.description || "";
  }
  async function loadTasks() {
    const result = await api("care", "/caretaker/tasks/today");
    tasks = result.gardens.flatMap(g => g.tasks.map(t => ({ ...t, gardenId: g.gardenId, plotCode: g.plotCode })));
    $("task").replaceChildren(new Option("작업을 골라 주세요", ""));
    for (const task of tasks) {
      const option = new Option(`${task.plotCode} ${task.title} (${task.status}, ${task.minutes}분)`, task.taskId);
      option.disabled = task.status === "DONE"; $("task").append(option);
    }
    const pending = tasks.find(t => t.status === "TODO");
    $("task").value = pending?.taskId || "";
    activeGarden = result.gardens[0]?.gardenId || activeGarden;
    $("weatherNotice").textContent = result.weatherNotice;
    clearTask();
    tell(pending ? "작업을 고르고 말로 남겨 주세요." : "지금 할 수 있는 작업을 모두 마쳤어요.");
  }
  function setAudio(file) {
    // 다른 녹음을 골랐을 때 이전에 확정한 메모로 작업을 마치지 않도록 한다.
    selectionVersion++;
    memo = null;
    $("transcript").value = ""; $("summary").value = ""; $("memoState").textContent = "";
    $("issues").textContent = "";
    audio = file;
    if (playbackUrl) URL.revokeObjectURL(playbackUrl);
    playbackUrl = URL.createObjectURL(file); $("playback").src = playbackUrl;
  }
  function showMemo(result) {
    memo = result;
    $("transcript").value = result.transcript || ""; $("summary").value = result.summary || "";
    const state = { PROCESSING: "분석하고 있어요", READY: "정리가 끝났어요", STT_FAILED: "음성을 듣지 못했어요. 다시 시도해 주세요", LLM_FAILED: "원문은 저장했어요. 문장 정리는 다시 시도할 수 있어요", INVALID_AUDIO: "음성 형식과 60초 제한을 확인해 주세요" };
    $("memoState").textContent = result.status === "CONFIRMED" ? "가족에게 보낼 문장을 확정했어요" : state[result.processingStatus] || "처리 상태를 확인해 주세요";
    $("issues").textContent = result.issues?.length ? "확인할 점: " + result.issues.map(code => issueLabels[code] || code).join(", ") : "자동으로 표시된 이상 징후는 없어요. 실제 상태는 직접 확인해 주세요.";
    tell(result.processingStatus === "READY" ? "원문과 정리한 문장을 확인해 주세요." : "처리 상태를 확인해 주세요. 원문이 있으면 내용을 확인한 뒤 보낼 수 있어요.");
  }
  action("loginCare", async () => { await login("care", "soonja"); await loadTasks(); });
  action("loginOwner", async () => { await login("owner", "minji"); await loadFamily(); });
  action("loginAdmin", () => login("admin", "demo_admin"));
  action("loadTasks", loadTasks);
  $("task").addEventListener("change", clearTask);
  $("audioFile").addEventListener("change", () => { if ($("audioFile").files[0]) setAudio($("audioFile").files[0]); });
  action("record", async () => {
    taskId();
    if (!navigator.mediaDevices || !window.MediaRecorder) throw new Error("localhost의 Chrome 또는 음성 파일 선택을 사용해 주세요.");
    if (recorder?.state === "recording") throw new Error("이미 녹음하고 있어요.");
    const version = selectionVersion;
    const recordStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream = recordStream;
    if (version !== selectionVersion) { recordStream.getTracks().forEach(track => track.stop()); throw new Error("작업이 바뀌었어요. 다시 녹음해 주세요."); }
    const preferred = ["audio/webm;codecs=opus", "audio/mp4"].find(type => MediaRecorder.isTypeSupported(type));
    let currentRecorder;
    try { currentRecorder = new MediaRecorder(recordStream, preferred ? { mimeType: preferred } : {}); }
    catch (error) { recordStream.getTracks().forEach(track => track.stop()); throw error; }
    recorder = currentRecorder;
    let recordTimer;
    const chunks = [];
    currentRecorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data); };
    currentRecorder.onstop = () => {
      clearTimeout(recordTimer); recordStream.getTracks().forEach(track => track.stop());
      if (recorder === currentRecorder) $("audioFile").disabled = false;
      if (version !== selectionVersion) return;
      const type = currentRecorder.mimeType, extension = type.includes("mp4") ? "mp4" : "webm";
      setAudio(new File(chunks, "recording." + extension, { type }));
      $("stopRecord").disabled = true; $("recordStatus").textContent = "녹음 완료";
    };
    currentRecorder.start(); $("audioFile").disabled = true; $("stopRecord").disabled = false; $("recordStatus").textContent = "녹음 중, 59초에 자동 종료";
    // 인코더의 끝부분이 붙어 서버의 60초 제한을 넘지 않도록 여유를 둔다.
    recordTimer = setTimeout(() => { if (currentRecorder.state === "recording") currentRecorder.stop(); }, 59000);
  });
  $("stopRecord").addEventListener("click", () => { if (recorder?.state === "recording") recorder.stop(); });
  action("sendAudio", async () => {
    const id = taskId(), version = selectionVersion; if (!audio) throw new Error("먼저 녹음하거나 음성 파일을 골라 주세요.");
    if (recorder?.state === "recording") throw new Error("녹음을 먼저 끝내 주세요.");
    memo = null;
    const form = new FormData(); form.append("audio", audio);
    tell("음성을 듣고 문장을 정리하고 있어요. 처음에는 모델 준비 시간이 걸릴 수 있어요.");
    const result = await api("care", `/caretaker/tasks/${id}/voice-memos`, { method: "POST", body: form });
    if (selectionVersion === version) showMemo(result);
  });
  action("recover", async () => { const id = taskId(), version = selectionVersion; const result = await api("care", `/caretaker/tasks/${id}/voice-memos/latest`); if (selectionVersion === version) showMemo(result); });
  action("retry", async () => {
    if (!memo?.retryable) throw new Error("재시도 가능한 메모가 없어요. 복구 조회로 상태를 확인해 주세요.");
    const version = selectionVersion, result = await api("care", `/caretaker/voice-memos/${memo.voiceMemoId}/retry`, { method: "POST" });
    if (selectionVersion === version) showMemo(result);
  });
  action("confirm", async () => {
    if (!memo || !memo.transcript || !$("summary").value.trim()) throw new Error("원문과 보낼 문장을 먼저 확인해 주세요.");
    const version = selectionVersion, confirming = memo, summary = $("summary").value;
    const result = await api("care", `/caretaker/voice-memos/${memo.voiceMemoId}/confirm`, { method: "POST", body: { summary: $("summary").value } });
    if (selectionVersion === version && memo === confirming) {
      if (summary !== memo.summary) memo.issues = [];
      memo.summary = summary; memo.status = result.status; memo.retryable = false;
      showMemo(memo); tell("메모를 확정했어요. 사진을 저장하고 작업을 완료해 주세요.");
    }
  });
  action("uploadPhoto", async () => {
    const id = taskId(), version = selectionVersion, file = $("photo").files[0]; if (!file) throw new Error("사진을 골라 주세요.");
    const form = new FormData(); form.append("photo", file);
    const result = await api("care", `/caretaker/tasks/${id}/photos`, { method: "POST", body: form });
    if (selectionVersion === version) { photoId = result.photoId; tell("사진을 저장했어요. 사진만으로 실제 작업 완료를 증명하는 것은 아니에요."); }
  });
  action("complete", async () => {
    const id = taskId();
    if (recorder?.state === "recording") throw new Error("녹음을 먼저 끝내 주세요.");
    if (audio && !memo) throw new Error("선택한 음성을 먼저 정리하고 확인해 주세요.");
    if (memo && memo.status !== "CONFIRMED") throw new Error("메모를 먼저 확정해 주세요.");
    const result = await api("care", `/caretaker/tasks/${id}/complete`, { method: "POST", body: { photoId, voiceMemoId: memo?.voiceMemoId || null } });
    await loadTasks(); tell(`작업 완료. 남은 작업 ${result.remainingTasks}개. 가족 기록에서 원문과 요약을 확인할 수 있어요.`);
  });
  action("checkStage", async () => {
    await api("care", `/gardens/${activeGarden}/stage-checks`, { method: "POST", body: { observed: $("observed").value } });
    tell("작물 모습을 확인했어요.");
  });
  async function loadDiary(append = false) {
    const query = diaryCursor && append ? "?cursor=" + encodeURIComponent(diaryCursor) : "";
    const result = await api("owner", `/gardens/${activeGarden}/diary` + query);
    if (!append) $("diary").replaceChildren();
    for (const item of result.items) {
      const article = document.createElement("article"), title = document.createElement("h3"), summary = document.createElement("p");
      title.textContent = item.date + " / " + (item.caretakerName || "성장 소식"); summary.textContent = item.summary;
      article.append(title, summary);
      if (item.transcript) { const original = document.createElement("p"); original.textContent = "말씀하신 원문: " + item.transcript; article.append(original); }
      if (item.photoUrl) { const image = document.createElement("img"); image.src = item.photoUrl; image.alt = "돌봄 인증 사진"; article.append(image); }
      $("diary").append(article);
    }
    diaryCursor = result.nextCursor; $("moreDiary").hidden = !diaryCursor;
  }
  async function loadFamily() {
    const growth = await api("owner", `/gardens/${activeGarden}/growth`);
    $("growth").textContent = `${growth.stage} / ${(growth.progress * 100).toFixed(0)}%\n${growth.explanation}`;
    const harvest = await api("owner", `/gardens/${activeGarden}/harvest`);
    $("harvest").textContent = `${harvest.expectedDate || "예측일 없음"}\n${harvest.message}`;
    diaryCursor = null; await loadDiary(); tell("가족 기록을 불러왔어요.");
  }
  action("loadFamily", loadFamily); action("moreDiary", () => loadDiary(true));
  function readAloud(text) {
    if (!window.speechSynthesis || !window.SpeechSynthesisUtterance) throw new Error("이 브라우저는 읽어주기를 지원하지 않아요.");
    if (!text?.trim()) throw new Error("먼저 읽을 내용을 불러와 주세요.");
    window.speechSynthesis.cancel();
    const speech = new window.SpeechSynthesisUtterance(text); speech.lang = "ko-KR"; speech.rate = .9;
    window.speechSynthesis.speak(speech);
  }
  action("readTask", () => readAloud($("taskDescription").textContent));
  action("readSummary", () => readAloud($("summary").value));
  document.querySelectorAll("[data-weather]").forEach(button => button.addEventListener("click", async () => {
    try { await api("admin", "/demo/weather", { method: "PUT", headers: { "X-Demo-Mode": "true" }, body: { condition: button.dataset.weather } }); if (tokens.care) await loadTasks(); else tell("날씨를 바꿨어요."); }
    catch (error) { tell(error.message); }
  }));
  window.addEventListener("beforeunload", () => { window.speechSynthesis?.cancel(); stream?.getTracks().forEach(track => track.stop()); if (playbackUrl) URL.revokeObjectURL(playbackUrl); });
})();
