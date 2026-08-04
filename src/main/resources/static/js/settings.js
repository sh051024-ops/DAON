// ============================================================
// 설정 페이지 (settings.html) 전용 스크립트
// - 기본 정보: DB(H2)에 실제 저장/조회
// - AI 연동: 서버의 OpenAI 연동 상태를 실제로 조회/테스트
// - 계정 및 보안: 로그인 세션 표시, 비밀번호 변경(users.json 실제 갱신), 로그아웃
// ============================================================

(function () {
  var toast = document.getElementById("toast");
  var toastTimer = null;
  function showToast(msg) {
    toast.textContent = msg;
    toast.classList.add("is-visible");
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.classList.remove("is-visible"); }, 2400);
  }

  function showFieldMsg(el, msg, ok) {
    el.textContent = msg;
    el.className = "field-msg show " + (ok ? "ok" : "fail");
  }

  async function fetchJson(url, options) {
    var res = await fetch(url, options);
    if (!res.ok) throw new Error("HTTP " + res.status);
    return res.json();
  }

  // ----- 탭 전환 -----
  var tabs = document.querySelectorAll(".settings-tab");
  tabs.forEach(function (tab) {
    tab.addEventListener("click", function () {
      tabs.forEach(function (t) { t.classList.remove("active"); });
      document.querySelectorAll(".settings-section").forEach(function (s) { s.classList.remove("active"); });
      tab.classList.add("active");
      document.getElementById("section-" + tab.dataset.tab).classList.add("active");
    });
  });

  // ----- 기본 정보 -----
  var academyForm = document.getElementById("academyForm");
  var academyMsg = document.getElementById("academyMsg");
  var fAcademyName = document.getElementById("academyName");
  var fPhone = document.getElementById("academyPhone");
  var fAddress = document.getElementById("academyAddress");
  var fBizNo = document.getElementById("academyBizNo");
  var fCeo = document.getElementById("academyCeo");
  var fBizType = document.getElementById("academyBizType");

  async function loadAcademySettings() {
    try {
      var s = await fetchJson("/api/settings/academy");
      fAcademyName.value = s.academyName || "";
      fPhone.value = s.phone || "";
      fAddress.value = s.address || "";
      fBizNo.value = s.businessRegNo || "";
      fCeo.value = s.ceoName || "";
      fBizType.value = s.businessType || "";
    } catch (e) {
      showFieldMsg(academyMsg, "불러오지 못했습니다.", false);
    }
  }

  academyForm.addEventListener("submit", async function (e) {
    e.preventDefault();
    try {
      await fetchJson("/api/settings/academy", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          academyName: fAcademyName.value.trim(),
          phone: fPhone.value.trim(),
          address: fAddress.value.trim(),
          businessRegNo: fBizNo.value.trim(),
          ceoName: fCeo.value.trim(),
          businessType: fBizType.value.trim()
        })
      });
      showToast("기본 정보가 저장되었습니다.");
      showFieldMsg(academyMsg, "저장되었습니다.", true);
    } catch (e) {
      showFieldMsg(academyMsg, "저장에 실패했습니다.", false);
    }
  });

  // ----- 운영 기준 -----
  var OPERATION_DEFAULTS = {
    riskThreshold: 60, warnThreshold: 70, watchThreshold: 80,
    absentStreakDays: 2, absentStreakLimit: 5,
    leadDoneThreshold: 90, leadProgressThreshold: 80,
    leadScheduledThreshold: 70, leadNewThreshold: 60
  };
  var OPERATION_FIELDS = Object.keys(OPERATION_DEFAULTS);

  var operationForm = document.getElementById("operationForm");
  var operationMsg = document.getElementById("operationMsg");
  var operationResetBtn = document.getElementById("operationResetBtn");
  var studentPreview = document.getElementById("studentPreview");

  function fillOperation(data) {
    OPERATION_FIELDS.forEach(function (key) {
      document.getElementById(key).value = data[key];
    });
    updateStudentPreview();
  }

  function readOperation() {
    var out = {};
    OPERATION_FIELDS.forEach(function (key) {
      out[key] = parseInt(document.getElementById(key).value, 10);
    });
    return out;
  }

  // 지금 입력값이면 학생 상태가 어떻게 나뉘는지 한 줄로 보여준다.
  function updateStudentPreview() {
    var risk = parseInt(document.getElementById("riskThreshold").value, 10);
    var warn = parseInt(document.getElementById("warnThreshold").value, 10);
    var watch = parseInt(document.getElementById("watchThreshold").value, 10);
    if (isNaN(risk) || isNaN(warn) || isNaN(watch)) {
      studentPreview.textContent = "—";
      return;
    }
    studentPreview.textContent =
      "출석률 " + risk + "% 미만 → 위험 · " +
      risk + "~" + (warn - 1) + "% → 주의 · " +
      warn + "~" + (watch - 1) + "% → 관심 · " +
      watch + "% 이상 → 수강중";
  }

  ["riskThreshold", "warnThreshold", "watchThreshold"].forEach(function (id) {
    document.getElementById(id).addEventListener("input", updateStudentPreview);
  });

  async function loadOperationSettings() {
    try {
      fillOperation(await fetchJson("/api/settings/operation"));
    } catch (e) {
      showFieldMsg(operationMsg, "불러오지 못했습니다.", false);
    }
  }

  operationForm.addEventListener("submit", async function (e) {
    e.preventDefault();
    try {
      var r = await fetchJson("/api/settings/operation", {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(readOperation())
      });
      showFieldMsg(operationMsg, r.message, r.success);
      if (r.success) showToast("운영 기준이 저장되었습니다.");
    } catch (e) {
      showFieldMsg(operationMsg, "저장에 실패했습니다.", false);
    }
  });

  operationResetBtn.addEventListener("click", function () {
    fillOperation(OPERATION_DEFAULTS);
    showFieldMsg(operationMsg, "기본값을 채웠습니다. 저장을 눌러야 반영됩니다.", true);
  });

  // ----- AI 연동 -----
  var aiStatusBadge = document.getElementById("aiStatusBadge");
  var aiModelLabel = document.getElementById("aiModelLabel");
  var aiTestBtn = document.getElementById("aiTestBtn");
  var aiTestResult = document.getElementById("aiTestResult");

  async function loadAiStatus() {
    try {
      var s = await fetchJson("/api/settings/ai-status");
      if (s.configured) {
        aiStatusBadge.textContent = "실제 연동";
        aiStatusBadge.className = "badge badge-green";
      } else {
        aiStatusBadge.textContent = "Mock 모드";
        aiStatusBadge.className = "badge badge-gray";
      }
      aiModelLabel.textContent = "모델: " + s.model;
    } catch (e) {
      aiStatusBadge.textContent = "확인 실패";
      aiStatusBadge.className = "badge badge-red";
    }
  }

  aiTestBtn.addEventListener("click", async function () {
    aiTestBtn.disabled = true;
    aiTestBtn.textContent = "테스트 중...";
    aiTestResult.className = "ai-test-result";
    try {
      var r = await fetchJson("/api/settings/ai-test", { method: "POST" });
      aiTestResult.textContent = r.message;
      aiTestResult.className = "ai-test-result show " + (r.success ? "ok" : "fail");
    } catch (e) {
      aiTestResult.textContent = "테스트 요청이 실패했습니다.";
      aiTestResult.className = "ai-test-result show fail";
    } finally {
      aiTestBtn.disabled = false;
      aiTestBtn.textContent = "연결 테스트";
    }
  });

  // ----- 계정 및 보안 -----
  var accountName = document.getElementById("accountName");
  var accountMeta = document.getElementById("accountMeta");
  var passwordForm = document.getElementById("passwordForm");
  var pwMsg = document.getElementById("pwMsg");
  var curPw = document.getElementById("curPw");
  var newPw = document.getElementById("newPw");
  var newPwConfirm = document.getElementById("newPwConfirm");
  var logoutBtn = document.getElementById("logoutBtn");

  function loadAccountInfo() {
    var auth = typeof getAuth === "function" ? getAuth() : null;
    if (!auth) return;
    accountName.textContent = auth.name || auth.id;
    accountMeta.textContent = "아이디: " + auth.id + " · 역할: " + (auth.role === "admin" ? "관리자" : auth.role);
  }

  passwordForm.addEventListener("submit", async function (e) {
    e.preventDefault();
    var auth = typeof getAuth === "function" ? getAuth() : null;
    if (!auth) return;

    if (newPw.value !== newPwConfirm.value) {
      showFieldMsg(pwMsg, "새 비밀번호가 서로 다릅니다.", false);
      return;
    }

    try {
      var r = await fetchJson("/api/auth/change-password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: auth.id, currentPassword: curPw.value, newPassword: newPw.value })
      });
      showFieldMsg(pwMsg, r.message, r.success);
      if (r.success) {
        showToast("비밀번호가 변경되었습니다.");
        passwordForm.reset();
      }
    } catch (e) {
      showFieldMsg(pwMsg, "요청이 실패했습니다.", false);
    }
  });

  logoutBtn.addEventListener("click", function () {
    if (confirm("로그아웃 하시겠어요?") && typeof logout === "function") logout();
  });

  // ----- 화면 (테마) -----
  var themeOptions = document.querySelectorAll(".theme-option");
  function markActiveTheme() {
    var current = typeof DaonTheme !== "undefined" ? DaonTheme.get() : "system";
    themeOptions.forEach(function (btn) {
      btn.classList.toggle("active", btn.dataset.themePref === current);
    });
  }
  themeOptions.forEach(function (btn) {
    btn.addEventListener("click", function () {
      DaonTheme.set(btn.dataset.themePref);
      markActiveTheme();
    });
  });
  markActiveTheme();

  // ----- 화면 (목록 표시 개수 / 사이드바 / 시작 화면) -----
  var prefListPageSize = document.getElementById("prefListPageSize");
  var prefSidebarCollapsed = document.getElementById("prefSidebarCollapsed");
  var prefStartPage = document.getElementById("prefStartPage");

  function loadDisplayPrefs() {
    if (typeof DaonPrefs === "undefined") return;
    prefListPageSize.value = String(DaonPrefs.get("listPageSize"));
    prefSidebarCollapsed.value = String(DaonPrefs.get("sidebarCollapsed"));
    prefStartPage.value = DaonPrefs.get("startPage");
  }

  prefListPageSize.addEventListener("change", function () {
    DaonPrefs.set("listPageSize", parseInt(prefListPageSize.value, 10));
    showToast("목록 표시 개수가 저장되었습니다.");
  });
  prefSidebarCollapsed.addEventListener("change", function () {
    DaonPrefs.set("sidebarCollapsed", prefSidebarCollapsed.value === "true");
    showToast("사이드바 기본 상태가 저장되었습니다.");
  });
  prefStartPage.addEventListener("change", function () {
    DaonPrefs.set("startPage", prefStartPage.value);
    showToast("시작 화면이 저장되었습니다.");
  });

  loadDisplayPrefs();

  loadAcademySettings();
  loadOperationSettings();
  loadAiStatus();
  loadAccountInfo();
})();
