// ============================================================
// 화면 환경설정 (설정 > 화면 탭)
//  - theme         : 라이트/다크/시스템
//  - listPageSize  : 목록 한 페이지 표시 개수
//  - sidebarCollapsed : 사이드바 접힘 여부
//  - startPage     : 로그인 후 처음 열 화면
// 깜빡임 없이 적용되도록 common.css 링크보다 먼저 <head> 맨 위에서 동기 실행된다.
// ============================================================

(function () {
  var KEYS = {
    theme: "daon_theme",
    listPageSize: "daon_list_page_size",
    sidebarCollapsed: "daon_sidebar_collapsed",
    startPage: "daon_start_page"
  };
  var DEFAULTS = {
    theme: "system",          // "light" | "dark" | "system"
    listPageSize: 20,
    sidebarCollapsed: false,
    startPage: "/index.html"
  };

  var mql = window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;

  function read(name) {
    try {
      var raw = localStorage.getItem(KEYS[name]);
      if (raw === null) return DEFAULTS[name];
      if (name === "listPageSize") {
        var n = parseInt(raw, 10);
        return isNaN(n) ? DEFAULTS[name] : n;
      }
      if (name === "sidebarCollapsed") return raw === "true";
      return raw;
    } catch (e) {
      return DEFAULTS[name];
    }
  }

  function write(name, value) {
    try { localStorage.setItem(KEYS[name], String(value)); } catch (e) { /* 저장 실패해도 화면 반영은 계속 */ }
  }

  // ----- 테마 -----
  function resolveTheme(pref) {
    if (pref === "dark" || pref === "light") return pref;
    return mql && mql.matches ? "dark" : "light";
  }

  function applyTheme(pref) {
    document.documentElement.setAttribute("data-theme", resolveTheme(pref));
  }

  // ----- 사이드바 접힘 -----
  // <html data-sidebar="collapsed">를 첫 렌더 전에 달아둬야, 나중에 주입되는
  // 사이드바와 그 자리표시자가 같은 폭으로 그려진다.
  function applySidebar(collapsed) {
    if (collapsed) {
      document.documentElement.setAttribute("data-sidebar", "collapsed");
    } else {
      document.documentElement.removeAttribute("data-sidebar");
    }
  }

  applyTheme(read("theme"));
  applySidebar(read("sidebarCollapsed"));

  if (mql) {
    mql.addEventListener("change", function () {
      if (read("theme") === "system") applyTheme("system");
    });
  }

  window.DaonPrefs = {
    defaults: DEFAULTS,
    get: read,
    set: function (name, value) {
      write(name, value);
      if (name === "theme") applyTheme(value);
      if (name === "sidebarCollapsed") applySidebar(value);
    }
  };

  // 기존 코드 호환용 (settings.js에서 쓰던 이름)
  window.DaonTheme = {
    get: function () { return read("theme"); },
    set: function (pref) { window.DaonPrefs.set("theme", pref); }
  };
})();
