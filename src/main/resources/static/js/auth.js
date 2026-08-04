// ---- DAON EDU 로그인 (JSON 기반 프론트 인증, 데모용) ----
// 주의: 데모 목적이라 계정 정보를 클라이언트에서 읽어 검증한다.
// 실제 서비스에서는 인증을 서버(Spring Security 등)에서 처리해야 한다.

const AUTH_KEY = "daon_auth";
const START_PAGE_KEY = "daon_start_page";

// 로그인 후 열 화면 (설정 > 화면에서 고른 값).
// 이 페이지는 theme.js를 안 불러오므로 저장값을 직접 읽는다.
// 저장된 값이 이상해도 엉뚱한 곳으로 가지 않도록 허용 목록 안에서만 쓴다.
const ALLOWED_START_PAGES = [
  "/index.html",
  "/pages/leads.html",
  "/pages/students.html",
  "/pages/attendance.html",
  "/pages/courses.html",
  "/pages/cs.html",
  "/pages/employment.html",
  "/pages/stats.html"
];

function getStartPage() {
  try {
    const saved = localStorage.getItem(START_PAGE_KEY);
    if (saved && ALLOWED_START_PAGES.indexOf(saved) !== -1) return saved;
  } catch (e) { /* 접근 불가면 기본값 */ }
  return "/index.html";
}

// 이미 로그인돼 있으면 바로 시작 화면으로
if (getAuth()) {
  location.replace(getStartPage());
}

document.addEventListener("DOMContentLoaded", function () {
  const form = document.getElementById("loginForm");
  const idInput = document.getElementById("loginId");
  const pwInput = document.getElementById("loginPw");
  const keep = document.getElementById("keepLogin");
  const errorBox = document.getElementById("loginError");
  const pwToggle = document.getElementById("pwToggle");
  // 비밀번호 표시/숨김
  pwToggle.addEventListener("click", function () {
    const show = pwInput.type === "password";
    pwInput.type = show ? "text" : "password";
    pwToggle.style.color = show ? "#2f6fed" : "";
  });

  // 로그인 제출
  form.addEventListener("submit", function (e) {
    e.preventDefault();
    attemptLogin(idInput.value.trim(), pwInput.value, keep.checked, errorBox);
  });

});

function attemptLogin(id, pw, keep, errorBox) {
  errorBox.hidden = true;

  if (!id || !pw) {
    showError(errorBox, "아이디와 비밀번호를 입력하세요.");
    return;
  }

  fetch("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: id, pw: pw })
  })
    .then(function (res) {
      if (!res.ok) throw new Error("HTTP " + res.status);
      return res.json();
    })
    .then(function (data) {
      if (!data.success) {
        showError(errorBox, data.message || "아이디 또는 비밀번호가 올바르지 않습니다.");
        return;
      }
      const user = data.user;
      // 로그인 성공: 비밀번호는 저장하지 않는다
      const session = { id: user.id, name: user.name, role: user.role, photo: user.photo };
      const store = keep ? localStorage : sessionStorage;
      store.setItem(AUTH_KEY, JSON.stringify(session));
      location.replace(getStartPage());
    })
    .catch(function () {
      showError(errorBox, "로그인 처리 중 오류가 발생했습니다.");
    });
}

function showError(box, msg) {
  box.textContent = msg;
  box.hidden = false;
}

// localStorage → sessionStorage 순으로 세션 조회
function getAuth() {
  const raw = localStorage.getItem(AUTH_KEY) || sessionStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch (e) { return null; }
}
