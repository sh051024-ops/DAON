// ---- 접근 가드: 로그인 안 했으면 로그인 페이지로 (데모용 프론트 인증) ----
const AUTH_KEY = "daon_auth";
const AUTH = getAuth();
if (!AUTH) {
  location.replace("/login.html");
}

function getAuth() {
  const raw = localStorage.getItem(AUTH_KEY) || sessionStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try { return JSON.parse(raw); } catch (e) { return null; }
}

function logout() {
  localStorage.removeItem(AUTH_KEY);
  sessionStorage.removeItem(AUTH_KEY);
  location.replace("/login.html");
}

// 공통 조각(사이드바/상단바)을 각 페이지에 주입한 뒤, 주입이 모두 끝나면 한 번만 초기화한다.
Promise.all(
  Array.from(document.querySelectorAll("[data-include]")).map(function (el) {
    return fetch(el.getAttribute("data-include"))
      .then(function (res) { return res.text(); })
      .then(function (html) { el.outerHTML = html; });
  })
).then(init);

function init() {
  markActiveNav();
  bindSidebar();
  bindDatePicker();
  applyAuthUser();
  bindAutoHideScrollbars();
}

// 카드 안 리스트(.list-scroll)의 세로 스크롤바를 평소엔 숨기고,
// 스크롤하는 동안만 잠깐 보였다가 다시 숨긴다.
function bindAutoHideScrollbars() {
  document.querySelectorAll(".list-scroll").forEach(function (el) {
    let hideTimer = null;
    el.addEventListener(
      "scroll",
      function () {
        el.classList.add("is-scrolling");
        clearTimeout(hideTimer);
        hideTimer = setTimeout(function () {
          el.classList.remove("is-scrolling");
        }, 900);
      },
      { passive: true }
    );
  });
}

// 로그인한 사용자 이름을 상단바 프로필에 반영하고, 프로필 드롭다운 메뉴를 연결
function applyAuthUser() {
  if (AUTH) {
    const nameEl = document.querySelector(".profile-name");
    if (nameEl && AUTH.name) nameEl.textContent = AUTH.name;
    const avatarEl = document.getElementById("profileAvatar");
    if (avatarEl && AUTH.photo) avatarEl.src = AUTH.photo;
  }

  const btn = document.getElementById("profileBtn");
  const dropdown = document.getElementById("profileDropdown");
  if (!btn || !dropdown) return;

  function close() {
    dropdown.hidden = true;
    btn.setAttribute("aria-expanded", "false");
  }
  btn.addEventListener("click", function (e) {
    e.stopPropagation();
    dropdown.hidden = !dropdown.hidden;
    btn.setAttribute("aria-expanded", String(!dropdown.hidden));
  });
  dropdown.addEventListener("click", function (e) { e.stopPropagation(); });
  document.addEventListener("click", close);
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") close(); });

  // 로그아웃: 실제 동작
  const logoutBtn = document.getElementById("menuLogout");
  if (logoutBtn) logoutBtn.addEventListener("click", function () {
    if (confirm("로그아웃 하시겠어요?")) logout();
  });
}

// body의 data-page와 같은 값을 가진 메뉴를 활성 상태로 표시
function markActiveNav() {
  const current = document.body.getAttribute("data-page");
  document.querySelectorAll(".nav-item").forEach(function (link) {
    link.classList.toggle("active", link.getAttribute("data-page") === current);
  });
}

// 사이드바 접기 상태는 설정 > 화면의 "사이드바" 옵션과 같은 저장소(DaonPrefs)를 공유한다.
function bindSidebar() {
  const btn = document.getElementById("collapseBtn");
  if (!btn) return;
  btn.addEventListener("click", function () {
    const collapsed = document.documentElement.getAttribute("data-sidebar") === "collapsed";
    if (typeof DaonPrefs !== "undefined") {
      DaonPrefs.set("sidebarCollapsed", !collapsed);
    } else {
      document.documentElement.setAttribute("data-sidebar", collapsed ? "" : "collapsed");
    }
  });
}

// 날짜 pill을 누르면 캘린더 드롭다운 열기/닫기, 날짜/범위/월 선택
function bindDatePicker() {
  const picker = document.querySelector(".date-picker");
  const btn = document.getElementById("datePillBtn");
  const dropdown = document.getElementById("dateDropdown");
  const label = document.getElementById("datePillLabel");
  const grid = document.getElementById("calGrid");
  const dayView = document.getElementById("calDayView");
  const monthView = document.getElementById("calMonthView");
  const monthsBox = document.getElementById("calMonths");
  const title = document.getElementById("calTitle");
  const prevBtn = document.getElementById("calPrev");
  const nextBtn = document.getElementById("calNext");
  const rangeToggle = document.getElementById("calRangeToggle");
  if (!picker || !btn || !dropdown || !grid) return;

  const DOW = ["일", "월", "화", "수", "목", "금", "토"];
  // 이 데모의 기준 '오늘'. 대시보드 데이터가 2024-05-21 기준이라 그에 맞춘다.
  // 실제 오늘로 쓰려면 이 줄을 new Date() 로 바꾸면 된다.
  const TODAY = new Date();

  function ymd(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
  function fmt(d) {
    return d.getFullYear() + "." + String(d.getMonth() + 1).padStart(2, "0") +
      "." + String(d.getDate()).padStart(2, "0") + " (" + DOW[d.getDay()] + ")";
  }

  // 상태
  let viewYear = TODAY.getFullYear();
  let viewMonth = TODAY.getMonth();
  let mode = "day";                 // "day" | "month"
  let selStart = ymd(TODAY); // 선택 시작(단일이면 이 값만)
  let selEnd = null;                // 범위 종료(없으면 단일)
  let rangeMode = false;
  let pendingStart = null;          // 범위 첫 클릭 대기값

  // 초기 날짜 레이블 설정
  label.textContent = fmt(selStart);

  function open() {
    dropdown.hidden = false;
    picker.classList.add("is-open");
    btn.setAttribute("aria-expanded", "true");
    mode = "day";
    render();
  }
  function close() {
    dropdown.hidden = true;
    picker.classList.remove("is-open");
    btn.setAttribute("aria-expanded", "false");
  }

  function updateTitle() {
    title.textContent = mode === "day"
      ? viewYear + "년 " + (viewMonth + 1) + "월"
      : viewYear + "년";
  }

  function sameDay(a, b) { return a && b && a.getTime() === b.getTime(); }
  function between(d, lo, hi) { return d.getTime() > lo.getTime() && d.getTime() < hi.getTime(); }

  function renderDays() {
    grid.querySelectorAll(".cal-day").forEach(function (n) { n.remove(); });
    const first = new Date(viewYear, viewMonth, 1);
    const gridStart = new Date(viewYear, viewMonth, 1 - first.getDay()); // 그 주 일요일
    for (let i = 0; i < 42; i++) {
      const d = new Date(gridStart.getFullYear(), gridStart.getMonth(), gridStart.getDate() + i);
      const b = document.createElement("button");
      b.type = "button";
      b.className = "cal-day";
      b.textContent = d.getDate();
      b.dataset.ts = d.getTime();
      if (d.getMonth() !== viewMonth) b.classList.add("cal-muted");
      // 선택 표시
      if (selEnd) {
        if (sameDay(d, selStart) || sameDay(d, selEnd)) b.classList.add("cal-selected");
        else if (between(d, selStart, selEnd)) b.classList.add("cal-in-range");
      } else if (sameDay(d, selStart)) {
        b.classList.add("cal-selected");
      }
      grid.appendChild(b);
    }
  }

  function renderMonths() {
    monthsBox.innerHTML = "";
    for (let m = 0; m < 12; m++) {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "cal-month";
      b.textContent = (m + 1) + "월";
      b.dataset.month = m;
      if (m === viewMonth) b.classList.add("cal-month-current");
      monthsBox.appendChild(b);
    }
  }

  function render() {
    updateTitle();
    if (mode === "day") {
      dayView.hidden = false;
      monthView.hidden = true;
      renderDays();
    } else {
      dayView.hidden = true;
      monthView.hidden = false;
      renderMonths();
    }
  }

  // 단일 선택
  function pickSingle(d) {
    selStart = d;
    selEnd = null;
    pendingStart = null;
    label.textContent = fmt(d);
    viewYear = d.getFullYear();
    viewMonth = d.getMonth();
    pulseDashboard();
    close();
  }

  // 범위 확정
  function pickRange(lo, hi) {
    selStart = lo;
    selEnd = hi;
    pendingStart = null;
    label.textContent = fmt(lo) + " ~ " + fmt(hi);
    pulseDashboard();
    close();
  }

  // ----- 이벤트 -----
  btn.addEventListener("click", function (e) {
    e.stopPropagation();
    dropdown.hidden ? open() : close();
  });
  dropdown.addEventListener("click", function (e) { e.stopPropagation(); });
  document.addEventListener("click", close);
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") close(); });

  // 화살표: 일 보기면 월 이동, 월 보기면 연 이동
  prevBtn.addEventListener("click", function () {
    if (mode === "day") { const d = new Date(viewYear, viewMonth - 1, 1); viewYear = d.getFullYear(); viewMonth = d.getMonth(); }
    else { viewYear -= 1; }
    render();
  });
  nextBtn.addEventListener("click", function () {
    if (mode === "day") { const d = new Date(viewYear, viewMonth + 1, 1); viewYear = d.getFullYear(); viewMonth = d.getMonth(); }
    else { viewYear += 1; }
    render();
  });

  // 제목 클릭: 일↔월 보기 전환
  title.addEventListener("click", function () {
    mode = mode === "day" ? "month" : "day";
    render();
  });

  // 월 클릭 → 해당 월의 일 보기로
  monthsBox.addEventListener("click", function (e) {
    const m = e.target.closest(".cal-month");
    if (!m) return;
    viewMonth = parseInt(m.dataset.month, 10);
    mode = "day";
    render();
  });

  // 날짜 클릭 (동적 생성이므로 위임)
  grid.addEventListener("click", function (e) {
    const cell = e.target.closest(".cal-day");
    if (!cell) return;
    const d = new Date(parseInt(cell.dataset.ts, 10));

    if (rangeMode) {
      if (pendingStart === null) {
        pendingStart = d;
        selStart = d;
        selEnd = null;
        render();
      } else {
        const lo = pendingStart <= d ? pendingStart : d;
        const hi = pendingStart <= d ? d : pendingStart;
        pickRange(lo, hi);
      }
    } else {
      pickSingle(d);
    }
  });

  // 빠른 선택
  dropdown.querySelectorAll("[data-quick]").forEach(function (qb) {
    qb.addEventListener("click", function () {
      const kind = qb.dataset.quick;
      if (kind === "today") {
        pickSingle(ymd(TODAY));
      } else if (kind === "yesterday") {
        const y = new Date(TODAY); y.setDate(y.getDate() - 1);
        pickSingle(ymd(y));
      } else if (kind === "week") {
        const start = new Date(TODAY); start.setDate(start.getDate() - start.getDay()); // 그 주 일요일
        const end = new Date(start); end.setDate(start.getDate() + 6);                   // 토요일
        viewYear = TODAY.getFullYear(); viewMonth = TODAY.getMonth();
        pickRange(ymd(start), ymd(end));
      } else if (kind === "month") {
        const start = new Date(TODAY.getFullYear(), TODAY.getMonth(), 1);
        const end = new Date(TODAY.getFullYear(), TODAY.getMonth() + 1, 0);              // 말일
        pickRange(ymd(start), ymd(end));
      }
    });
  });

  // 범위 선택 모드 토글
  rangeToggle.addEventListener("click", function () {
    rangeMode = !rangeMode;
    pendingStart = null;
    rangeToggle.classList.toggle("is-active", rangeMode);
  });
}

// 대시보드 수치들에 하얀 배경에서 페이드인 효과 (변경됐다는 시각적 신호만)
function pulseDashboard() {
  const sel = ".kpi-value, .kpi-delta, .funnel-stages, .funnel-summary, .donut-wrap, .linechart-wrap";
  document.querySelectorAll(sel).forEach(function (el) {
    el.classList.remove("data-refresh");
    void el.offsetWidth; // 리플로우로 애니메이션 재시작
    el.classList.add("data-refresh");
  });
}
