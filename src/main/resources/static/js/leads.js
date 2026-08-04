// ============================================================
// 리드 관리 페이지 — 프론트엔드 전용 동작
// (DB/백엔드 연동 전까지 메모리 배열/상수로 상태를 관리한다)
// ============================================================

(function () {
  var STATUS_BADGE = {
    "신규": "badge-blue",
    "상담예정": "badge-gray",
    "상담진행": "badge-amber",
    "등록완료": "badge-green",
    "이탈": "badge-red"
  };

  var MANAGERS = [];
  var SOURCES = [];
  var NEW_LEAD_AVATARS = ["/images/default_avatar_man.png", "/images/default_avatar_woman.png"];
  var PAGE_SIZE = typeof DaonPrefs !== "undefined" ? DaonPrefs.get("listPageSize") : 20;

  function managerAvatar(name) {
    var m = MANAGERS.filter(function (x) { return x.name === name; })[0];
    return m ? m.avatar : "/images/default_avatar_man.png";
  }

  function todayStr() {
    var d = new Date();
    return d.getFullYear() + "." + String(d.getMonth() + 1).padStart(2, "0") + "." + String(d.getDate()).padStart(2, "0");
  }

  function maskPhone(phone) {
    var digits = phone.replace(/[^0-9]/g, "");
    if (digits.length === 11) return digits.slice(0, 3) + "-" + digits.slice(3, 7) + "-****";
    if (digits.length === 10) return digits.slice(0, 3) + "-" + digits.slice(3, 6) + "-****";
    return phone;
  }

  function escapeHtml(str) {
    return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function fmtNum(n) {
    return n.toLocaleString("ko-KR");
  }

  // 문자열 시드 기반의 결정론적 소수 (0~1) — 기수/기간 조합마다 늘 같은 값이 나오도록
  function seedFrac(str) {
    var h = 0;
    for (var i = 0; i < str.length; i++) { h = (h * 31 + str.charCodeAt(i)) >>> 0; }
    return (h % 1000) / 1000;
  }

  // ----- 초기 리드 데이터 (데모용) -----
  var nextId = 1000;
  var leads = [];

  var COHORTS = ["전체 기수"];
  var PERIODS = ["전체 기간", "이번 달", "지난 달", "최근 3개월"];

  function getFunnelStages(cohort, period) {
    var filtered = leads.filter(function (l) {
      if (cohort !== "전체 기수" && l.className !== cohort) return false;
      return true;
    });

    var total = filtered.length;
    var inProgress = filtered.filter(function (l) {
      return l.status === "상담예정" || l.status === "상담진행" || l.status === "등록완료";
    }).length;
    var regConsult = filtered.filter(function (l) {
      return l.status === "상담진행" || l.status === "등록완료";
    }).length;
    var enrolled = filtered.filter(function (l) {
      return l.status === "등록완료";
    }).length;

    var periodFactors = {
      "전체 기간": 1,
      "이번 달": 0.24,
      "지난 달": 0.21,
      "최근 3개월": 0.62
    };
    var factor = periodFactors[period] != null ? periodFactors[period] : 1;
    var valApplied = Math.max(1, Math.round(total * factor));
    var valInProgress = Math.min(valApplied, Math.max(0, Math.round(inProgress * factor)));
    var valRegConsult = Math.min(valInProgress, Math.max(0, Math.round(regConsult * factor)));
    var valEnrolled = Math.min(valRegConsult, Math.max(0, Math.round(enrolled * factor)));

    return [
      { label: "상담 신청", value: valApplied },
      { label: "상담 진행", value: valInProgress },
      { label: "등록 상담", value: valRegConsult },
      { label: "등록 완료", value: valEnrolled }
    ];
  }

  // ----- 필터/페이지 상태 -----
  var state = {
    status: "전체",
    manager: "전체",
    source: "전체",
    search: "",
    followupOnly: false,
    page: 1
  };
  var flowState = { cohort: "전체 기수", period: "전체 기간" };

  // ----- DOM 참조 -----
  var tbody = document.getElementById("leadsTbody");
  var paginationInfo = document.getElementById("paginationInfo");
  var paginationPages = document.getElementById("paginationPages");
  var searchInput = document.getElementById("searchInput");
  var filterTabs = document.getElementById("filterTabs");
  var toast = document.getElementById("toast");

  // ----- 리드 테이블 필터링 -----
  function getFilteredLeads() {
    return leads.filter(function (l) {
      if (state.followupOnly) return !!l.followUp;
      if (state.status !== "전체" && l.status !== state.status) return false;
      if (state.manager !== "전체" && l.manager !== state.manager) return false;
      if (state.source !== "전체" && l.source !== state.source) return false;
      if (state.search) {
        var q = state.search.toLowerCase();
        var hay = (l.name + " " + l.phone).toLowerCase();
        if (hay.indexOf(q) === -1) return false;
      }
      return true;
    });
  }

  function rowHtml(l) {
    var badgeClass = STATUS_BADGE[l.status] || "badge-gray";
    return (
      '<tr data-id="' + l.id + '">' +
        '<td><div class="cell-name"><span class="row-avatar"><img src="' + l.avatar + '" alt="" /></span><span class="row-title">' + escapeHtml(l.name) + '</span></div></td>' +
        '<td class="cell-contact">' + escapeHtml(l.phone) + '</td>' +
        '<td>' + escapeHtml(l.course) + '</td>' +
        '<td><span class="tag">' + escapeHtml(l.source) + '</span></td>' +
        '<td><div class="cell-manager"><span class="mini-avatar"><img src="' + managerAvatar(l.manager) + '" alt="" /></span>' + escapeHtml(l.manager) + '</div></td>' +
        '<td><span class="badge ' + badgeClass + '">' + escapeHtml(l.status) + '</span></td>' +
        '<td class="cell-contact">' + escapeHtml(l.lastContact) + '</td>' +
        '<td><div class="row-actions">' +
          '<span class="row-action" data-action="detail" data-id="' + l.id + '">상담기록</span>' +
          '<span class="action-divider">·</span>' +
          '<span class="row-action" data-action="edit" data-id="' + l.id + '">수정</span>' +
          '<span class="action-divider">·</span>' +
          '<span class="row-action row-action-danger" data-action="delete" data-id="' + l.id + '">삭제</span>' +
        '</div></td>' +
      '</tr>'
    );
  }

  function renderCounts() {
    document.getElementById("countAll").textContent = leads.length;
    document.getElementById("countNew").textContent = leads.filter(function (l) { return l.status === "신규"; }).length;
    document.getElementById("countScheduled").textContent = leads.filter(function (l) { return l.status === "상담예정"; }).length;
    document.getElementById("countProgress").textContent = leads.filter(function (l) { return l.status === "상담진행"; }).length;
    document.getElementById("countDone").textContent = leads.filter(function (l) { return l.status === "등록완료"; }).length;
    document.getElementById("countLost").textContent = leads.filter(function (l) { return l.status === "이탈"; }).length;
  }

  function renderTable() {
    renderCounts();
    var filtered = getFilteredLeads();
    var totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
    if (state.page > totalPages) state.page = totalPages;
    var start = (state.page - 1) * PAGE_SIZE;
    var pageItems = filtered.slice(start, start + PAGE_SIZE);

    if (pageItems.length === 0) {
      tbody.innerHTML = '<tr class="empty-row"><td colspan="8">조건에 맞는 리드가 없어요. 검색어나 필터를 확인해주세요.</td></tr>';
    } else {
      tbody.innerHTML = pageItems.map(rowHtml).join("");
    }

    paginationInfo.textContent = filtered.length === 0
      ? "전체 0건 중 0건 표시"
      : "전체 " + filtered.length + "건 중 " + (start + 1) + "–" + Math.min(start + PAGE_SIZE, filtered.length) + "건 표시";

    renderPagination(totalPages);
  }

  function renderPagination(totalPages) {
    var html = "";
    html += '<button class="page-btn nav-arrow" type="button" data-page="prev" ' + (state.page <= 1 ? "disabled" : "") + ' aria-label="이전">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px"><path d="M15 18l-6-6 6-6"/></svg></button>';
    for (var p = 1; p <= totalPages; p++) {
      html += '<button class="page-btn ' + (p === state.page ? "active" : "") + '" type="button" data-page="' + p + '">' + p + '</button>';
    }
    html += '<button class="page-btn nav-arrow" type="button" data-page="next" ' + (state.page >= totalPages ? "disabled" : "") + ' aria-label="다음">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="width:14px;height:14px"><path d="M9 6l6 6-6 6"/></svg></button>';
    paginationPages.innerHTML = html;
  }

  paginationPages.addEventListener("click", function (e) {
    var btn = e.target.closest(".page-btn");
    if (!btn || btn.disabled) return;
    var p = btn.getAttribute("data-page");
    if (p === "prev") state.page -= 1;
    else if (p === "next") state.page += 1;
    else state.page = parseInt(p, 10);
    renderTable();
  });

  function clearSpecialFilters() {
    state.followupOnly = false;
  }

  // ----- 검색 -----
  searchInput.addEventListener("input", function () {
    clearSpecialFilters();
    state.search = searchInput.value.trim();
    state.page = 1;
    renderTable();
  });

  // ----- 상태 탭 -----
  filterTabs.addEventListener("click", function (e) {
    var tab = e.target.closest(".filter-tab");
    if (!tab) return;
    clearSpecialFilters();
    filterTabs.querySelectorAll(".filter-tab").forEach(function (t) { t.classList.remove("active"); });
    tab.classList.add("active");
    state.status = tab.getAttribute("data-status");
    state.page = 1;
    renderTable();
  });

  // ----- 후속 연락 필요 패널의 "더보기" → 해당 4명만 필터링해서 목록으로 스크롤 -----
  document.getElementById("followUpMoreLink").addEventListener("click", function (e) {
    e.preventDefault();
    state.followupOnly = true;
    state.search = "";
    searchInput.value = "";
    filterTabs.querySelectorAll(".filter-tab").forEach(function (t) { t.classList.remove("active"); });
    state.page = 1;
    renderTable();
    document.querySelector(".table-card").scrollIntoView({ behavior: "smooth", block: "start" });
    showToast("후속 연락이 필요한 리드만 보여주고 있어요.");
  });

  // ----- 공용 드롭다운 -----
  var allDropdowns = [];
  function closeAllDropdowns() {
    allDropdowns.forEach(function (w) { w._closeMenu(); });
  }

  function setupDropdown(wrapId, btnId, menuId, labelId, options, labelPrefix, initial, onChange) {
    var wrap = document.getElementById(wrapId);
    var btn = document.getElementById(btnId);
    var menu = document.getElementById(menuId);
    var label = document.getElementById(labelId);
    var current = initial;

    function render() {
      var html = "";
      options.forEach(function (opt) {
        html += '<button type="button" class="dropdown-option ' + (current === opt ? "active" : "") + '" data-value="' + escapeHtml(opt) + '">' + escapeHtml(opt) + '</button>';
      });
      menu.innerHTML = html;
    }

    render();

    btn.addEventListener("click", function (e) {
      e.stopPropagation();
      var isHidden = menu.hasAttribute("hidden");
      closeAllDropdowns();
      if (isHidden) menu.removeAttribute("hidden");
    });

    menu.addEventListener("click", function (e) {
      var opt = e.target.closest(".dropdown-option");
      if (!opt) return;
      current = opt.getAttribute("data-value");
      label.textContent = current;
      render();
      menu.setAttribute("hidden", "");
      onChange(current);
    });

    wrap._closeMenu = function () { menu.setAttribute("hidden", ""); };
    allDropdowns.push(wrap);
  }

  function setupDropdowns() {
    setupDropdown("managerDropdown", "managerDropdownBtn", "managerDropdownMenu", "managerDropdownLabel",
      ["전체"].concat(MANAGERS.map(function (m) { return m.name; })), "담당자", "전체", function (val) {
        state.manager = val;
        clearSpecialFilters();
        state.page = 1;
        renderTable();
      });

    setupDropdown("sourceDropdown", "sourceDropdownBtn", "sourceDropdownMenu", "sourceDropdownLabel",
      ["전체"].concat(SOURCES), "유입 경로", "전체", function (val) {
        state.source = val;
        clearSpecialFilters();
        state.page = 1;
        renderTable();
      });

    setupDropdown("cohortDropdown", "cohortDropdownBtn", "cohortDropdownMenu", "cohortDropdownLabel",
      COHORTS, "기수", "전체 기수", function (val) {
        flowState.cohort = val;
        renderFlow();
      });

    setupDropdown("periodDropdown", "periodDropdownBtn", "periodDropdownMenu", "periodDropdownLabel",
      PERIODS, "기간", "전체 기간", function (val) {
        flowState.period = val;
        renderFlow();
      });
  }

  function setupModalSelectOptions() {
    var courseSelect = document.getElementById("fieldCourse");
    var sourceSelect = document.getElementById("fieldSource");
    var managerSelect = document.getElementById("fieldManager");
    if (!courseSelect || !sourceSelect || !managerSelect) return;
    
    var courses = {};
    leads.forEach(function (l) { if (l.course) courses[l.course] = true; });
    
    courseSelect.innerHTML = '<option value="">선택</option>' + Object.keys(courses).map(function (c) {
      return '<option>' + escapeHtml(c) + '</option>';
    }).join("");
    
    sourceSelect.innerHTML = '<option value="">선택</option>' + SOURCES.map(function (s) {
      return '<option>' + escapeHtml(s) + '</option>';
    }).join("");
    
    managerSelect.innerHTML = '<option value="">선택</option>' + MANAGERS.map(function (m) {
      return '<option>' + escapeHtml(m.name) + '</option>';
    }).join("");
  }

  function renderFollowUpList() {
    var container = document.getElementById("leads-followup-list");
    if (!container) return;
    
    var urgentLeads = leads.filter(function (l) { return !!l.followUp; }).slice(0, 4);
    if (urgentLeads.length === 0) {
      container.innerHTML = '<div class="list-row"><div class="row-main"><div class="row-title" style="color: var(--text-500);">후속 연락 대상이 없습니다.</div></div></div>';
      return;
    }
    
    container.innerHTML = urgentLeads.map(function (l) {
      var tag = l.followUp === "urgent" 
        ? '<span class="priority-tag priority-high">긴급</span>'
        : '<span class="priority-tag priority-mid">보통</span>';
      var subText = l.followUp === "urgent" ? "출석률 저조 및 케어 필요" : "상담 예정 상태";
      return (
        '<div class="list-row">' +
          '<span class="row-avatar"><img src="' + l.avatar + '" alt="' + escapeHtml(l.name) + '" /></span>' +
          '<div class="row-main">' +
            '<div class="row-title">' + escapeHtml(l.name) + '</div>' +
            '<div class="row-sub">' + escapeHtml(l.course) + ' · ' + subText + '</div>' +
          '</div>' +
          tag +
        '</div>'
      );
    }).join("");
  }

  document.addEventListener("click", closeAllDropdowns);

  // ----- 리드 흐름도 (단계별 카드 + 전환 화살표) -----
  var FLOW_STEP_META = [
    { icon: "i-report1", color: "var(--blue)", desc: "상담을 신청한 전체 리드 수" },
    { icon: "i-call", color: "var(--purple)", desc: "상담이 진행된 리드 수" },
    { icon: "i-bubble", color: "var(--teal)", desc: "등록 상담을 완료한 리드 수" },
    { icon: "i-check", color: "var(--green)", desc: "최종 등록을 완료한 리드 수" }
  ];

  function buildFlowSteps(stages) {
    var maxValue = stages[0].value || 1;
    var html = '<div class="flow-steps">';

    stages.forEach(function (s, i) {
      var meta = FLOW_STEP_META[i];
      var pct = Math.round((s.value / maxValue) * 100);
      var iconHtml = meta.icon === "i-check"
        ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" style="width:16px;height:16px"><path d="M20 6 9 17l-5-5"/></svg>'
        : '<span class="icon ' + meta.icon + '"></span>';

      html +=
        '<div class="flow-step">' +
          '<div class="flow-step-head">' +
            '<span class="flow-step-icon" style="color:' + meta.color + '">' + iconHtml + '</span>' +
            '<span class="flow-step-label">' + escapeHtml(s.label) + '</span>' +
          '</div>' +
          '<div class="flow-step-value">' + fmtNum(s.value) + '</div>' +
          '<div class="flow-step-bar"><div class="flow-step-bar-fill" style="width:' + pct + '%"></div></div>' +
          '<div class="flow-step-desc">' + meta.desc + '</div>' +
        '</div>';

      if (i < stages.length - 1) {
        var next = stages[i + 1];
        var rate = s.value > 0 ? (next.value / s.value * 100) : 0;
        var drop = s.value - next.value;
        html +=
          '<div class="flow-conn">' +
            '<div class="flow-conn-rate">' + rate.toFixed(1) + '%</div>' +
            '<svg class="flow-conn-arrow" viewBox="0 0 60 12" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><line x1="2" y1="6" x2="50" y2="6"/><path d="M44 1 L52 6 L44 11"/></svg>' +
            (drop > 0 ? '<div class="flow-conn-drop">이탈 ' + fmtNum(drop) + '</div>' : '') +
          '</div>';
      }
    });

    html += '</div>';
    return html;
  }

  function renderFlow() {
    var stages = getFunnelStages(flowState.cohort, flowState.period);
    document.getElementById("flowChartWrap").innerHTML = buildFlowSteps(stages);

    var applied = stages[0].value, enrolled = stages[3].value;
    var rate = applied > 0 ? (enrolled / applied * 100) : 0;
    document.getElementById("flowSummaryMain").innerHTML = "전체 전환율&nbsp;&nbsp;<b>" + rate.toFixed(1) + "%</b>";

    var deltaSeed = seedFrac(flowState.cohort + "|" + flowState.period);
    var deltaPct = (deltaSeed * 9 - 3).toFixed(1); // -3.0 ~ +6.0 사이 결정론적 값
    var isUp = parseFloat(deltaPct) >= 0;
    document.getElementById("flowSummarySub").innerHTML =
      "직전 기간 대비 <span class=\"" + (isUp ? "up" : "down") + "\">" + (isUp ? "▲ " : "▼ ") + Math.abs(deltaPct) + "%p</span>";

    // KPI 카드도 선택된 기수/기간에 맞춰 함께 갱신
    var totalLeads = applied;
    var newLeads = Math.round(stages[1].value * 0.28);
    var inProgress = stages[1].value;

    document.getElementById("kpiTotal").innerHTML = fmtNum(totalLeads) + '<span class="kpi-unit">건</span>';
    document.getElementById("kpiNew").innerHTML = fmtNum(newLeads) + '<span class="kpi-unit">건</span>';
    document.getElementById("kpiProgress").innerHTML = fmtNum(inProgress) + '<span class="kpi-unit">건</span>';
    document.getElementById("kpiRate").innerHTML = rate.toFixed(1) + '<span class="kpi-unit">%</span>';

    ["kpiTotalDelta", "kpiNewDelta", "kpiProgressDelta", "kpiRateDelta"].forEach(function (id, idx) {
      var seed = seedFrac(flowState.cohort + "|" + flowState.period + "|" + id);
      var pct = (seed * 12 - 2).toFixed(1);
      var up = parseFloat(pct) >= 0;
      var unit = idx === 3 ? "%p" : "건";
      var amount = idx === 3 ? Math.abs(pct) : Math.round(Math.abs(pct) / 100 * (idx === 0 ? totalLeads : idx === 1 ? newLeads : inProgress));
      document.getElementById(id).innerHTML =
        '<span class="' + (up ? "up" : "down") + '">' + (up ? "↑ " : "↓ ") + amount + unit + '</span> · 직전 기간 대비';
    });

    document.querySelectorAll(".kpi-value, .funnel-summary, .flow-chart-wrap").forEach(function (el) {
      el.classList.remove("data-refresh");
      void el.offsetWidth;
      el.classList.add("data-refresh");
    });
  }

  // ----- 토스트 -----
  var toastTimer = null;
  function showToast(msg) {
    toast.textContent = msg;
    toast.classList.add("is-visible");
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { toast.classList.remove("is-visible"); }, 2400);
  }

  // ----- 신규 리드 등록 / 수정 모달 (공용) -----
  var addModal = document.getElementById("addModal");
  var addForm = document.getElementById("addLeadForm");
  var addModalTitle = document.getElementById("addModalTitle");
  var addModalSubmitBtn = document.getElementById("addModalSubmitBtn");
  var modalMode = "add"; // "add" | "edit"
  var editingId = null;

  function setFieldValidity(fieldId, hintId, message) {
    document.getElementById(fieldId).classList.toggle("is-invalid", !!message);
    document.getElementById(hintId).textContent = message || "";
  }

  function openAddModal() {
    modalMode = "add";
    editingId = null;
    addForm.reset();
    setFieldValidity("fieldName", "hintName", "");
    setFieldValidity("fieldPhone", "hintPhone", "");
    addModalTitle.textContent = "신규 리드 등록";
    addModalSubmitBtn.textContent = "등록하기";
    addModal.removeAttribute("hidden");
  }

  function openEditModal(id) {
    var lead = leads.filter(function (l) { return l.id === id; })[0];
    if (!lead) return;
    modalMode = "edit";
    editingId = id;
    setFieldValidity("fieldName", "hintName", "");
    setFieldValidity("fieldPhone", "hintPhone", "");
    document.getElementById("fieldName").value = lead.name;
    document.getElementById("fieldPhone").value = lead.phone.replace(/\*/g, "");
    document.getElementById("fieldCourse").value = lead.course;
    document.getElementById("fieldSource").value = lead.source;
    document.getElementById("fieldManager").value = lead.manager;
    document.getElementById("fieldStatus").value = lead.status;
    document.getElementById("fieldMemo").value = lead.memo || "";
    addModalTitle.textContent = "리드 정보 수정";
    addModalSubmitBtn.textContent = "저장하기";
    addModal.removeAttribute("hidden");
  }

  function closeAddModal() {
    addModal.setAttribute("hidden", "");
  }

  document.getElementById("openAddModalBtn").addEventListener("click", openAddModal);
  document.getElementById("closeAddModalBtn").addEventListener("click", closeAddModal);
  document.getElementById("cancelAddModalBtn").addEventListener("click", closeAddModal);
  addModal.addEventListener("click", function (e) { if (e.target === addModal) closeAddModal(); });

  addForm.addEventListener("submit", function (e) {
    e.preventDefault();
    var name = document.getElementById("fieldName").value.trim();
    var phone = document.getElementById("fieldPhone").value.trim();
    var course = document.getElementById("fieldCourse").value;
    var source = document.getElementById("fieldSource").value;
    var manager = document.getElementById("fieldManager").value;
    var status = document.getElementById("fieldStatus").value;
    var memo = document.getElementById("fieldMemo").value.trim();

    var valid = true;
    setFieldValidity("fieldName", "hintName", "");
    setFieldValidity("fieldPhone", "hintPhone", "");

    if (!name) { setFieldValidity("fieldName", "hintName", "이름을 입력해주세요."); valid = false; }
    var phoneDigits = phone.replace(/[^0-9]/g, "");
    if (!phone || phoneDigits.length < 9) { setFieldValidity("fieldPhone", "hintPhone", "연락처를 정확히 입력해주세요."); valid = false; }
    if (!course || !source || !manager) {
      valid = false;
      showToast("관심 과정 · 유입 경로 · 담당자를 모두 선택해주세요.");
    }
    if (!valid) return;

    if (modalMode === "edit" && editingId !== null) {
      var lead = leads.filter(function (l) { return String(l.id) === String(editingId); })[0];
      if (lead) {
        fetch("/api/ai/update-lead", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            id: String(editingId),
            status: status,
            memo: memo,
            lastContact: todayStr()
          })
        })
        .then(function (res) { return res.json(); })
        .then(function (resData) {
          if (resData.success) {
            lead.name = name;
            lead.phone = maskPhone(phone);
            lead.course = course;
            lead.source = source;
            lead.manager = manager;
            lead.status = status;
            lead.memo = memo;
            lead.lastContact = todayStr();
            renderTable();
            renderFlow();
            renderFollowUpList();
            closeAddModal();
            showToast(name + " 님의 정보를 수정하고 저장했어요.");
          } else {
            showToast("수정 사항 저장에 실패했습니다.");
          }
        })
        .catch(function (err) {
          console.error("Failed to update lead:", err);
          showToast("서버 통신 오류가 발생했습니다.");
        });
      }
      return;
    }

    fetch("/api/ai/add-lead", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: name,
        phone: maskPhone(phone),
        course: course,
        source: source,
        manager: manager,
        status: status,
        memo: memo
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (resData) {
        if (resData.success) {
          // 서버에서 실시간 리스트를 재조회하여 완벽 동기화
          fetch("/api/ai/leads")
            .then(function (res2) { return res2.json(); })
            .then(function (data) {
              leads = data;

              clearSpecialFilters();
              state.status = "전체";
              state.manager = "전체";
              state.source = "전체";
              state.search = "";
              state.page = 1;
              searchInput.value = "";
              filterTabs.querySelectorAll(".filter-tab").forEach(function (t) {
                t.classList.toggle("active", t.getAttribute("data-status") === "전체");
              });
              document.getElementById("managerDropdownLabel").textContent = "담당자 전체";
              document.getElementById("sourceDropdownLabel").textContent = "유입 경로 전체";

              renderTable();
              renderFlow();
              renderFollowUpList();
              closeAddModal();
              showToast(name + " 님을 리드로 등록했어요.");
            });
        } else {
          showToast("리드 등록에 실패했습니다.");
        }
      })
      .catch(function (err) {
        console.error("Failed to add lead:", err);
        showToast("서버 통신 오류가 발생했습니다.");
      });
  });

  // ----- 상세 / 상담기록 모달 -----
  var detailModal = document.getElementById("detailModal");
  var currentDetailId = null;

  function openDetailModal(id) {
    var lead = leads.filter(function (l) { return String(l.id) === String(id); })[0];
    if (!lead) return;
    currentDetailId = String(id);
    document.getElementById("detailAvatar").src = lead.avatar;
    document.getElementById("detailName").textContent = lead.name;
    document.getElementById("detailCourse").textContent = lead.course;
    document.getElementById("detailPhone").textContent = lead.phone;
    document.getElementById("detailSource").textContent = lead.source;
    document.getElementById("detailManager").textContent = lead.manager;
    document.getElementById("detailLastContact").textContent = lead.lastContact;
    document.getElementById("detailStatus").value = lead.status;
    document.getElementById("detailMemo").value = lead.memo || "";
    detailModal.removeAttribute("hidden");
  }
  function closeDetailModal() {
    detailModal.setAttribute("hidden", "");
    currentDetailId = null;
  }

  document.getElementById("closeDetailModalBtn").addEventListener("click", closeDetailModal);
  document.getElementById("cancelDetailModalBtn").addEventListener("click", closeDetailModal);
  detailModal.addEventListener("click", function (e) { if (e.target === detailModal) closeDetailModal(); });

  document.getElementById("saveDetailModalBtn").addEventListener("click", function () {
    if (currentDetailId === null) return;
    var lead = leads.filter(function (l) { return String(l.id) === String(currentDetailId); })[0];
    if (!lead) return;
    
    var newStatus = document.getElementById("detailStatus").value;
    var newMemo = document.getElementById("detailMemo").value.trim();
    var today = todayStr();

    fetch("/api/ai/update-lead", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: String(currentDetailId),
        status: newStatus,
        memo: newMemo,
        lastContact: today
      })
    })
    .then(function (res) { return res.json(); })
    .then(function (resData) {
      if (resData.success) {
        lead.status = newStatus;
        lead.memo = newMemo;
        lead.lastContact = today;

        // Also add to counseling history timeline on the backend if a memo exists
        if (newMemo) {
          fetch("/api/ai/add-counseling", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              id: String(currentDetailId),
              type: "상담",
              summary: "상담 상세 기록 수정",
              memo: newMemo
            })
          });
        }

        renderTable();
        renderFlow();
        renderFollowUpList();
        closeDetailModal();
        showToast(lead.name + " 님의 상담 기록을 저장했어요.");
      } else {
        showToast("상담 기록 저장에 실패했습니다.");
      }
    })
    .catch(function (err) {
      console.error("Failed to save counseling log:", err);
      showToast("서버 통신 오류가 발생했습니다.");
    });
  });

  // ----- 삭제 -----
  function deleteLead(id) {
    var lead = leads.filter(function (l) { return l.id === id; })[0];
    if (!lead) return;
    var ok = confirm(lead.name + " 님을 리드 목록에서 삭제할까요? 이 작업은 되돌릴 수 없어요.");
    if (!ok) return;
    leads = leads.filter(function (l) { return l.id !== id; });
    renderTable();
    showToast(lead.name + " 님을 삭제했어요.");
  }

  // ----- 테이블 행 액션 위임 (상담기록 / 수정 / 삭제) -----
  tbody.addEventListener("click", function (e) {
    var target = e.target.closest("[data-action]");
    if (!target) return;
    var id = parseInt(target.getAttribute("data-id"), 10);
    var action = target.getAttribute("data-action");
    if (action === "detail") openDetailModal(id);
    else if (action === "edit") openEditModal(id);
    else if (action === "delete") deleteLead(id);
  });

  // ----- 내보내기 (현재 필터 기준 CSV 다운로드) -----
  document.getElementById("exportBtn").addEventListener("click", function () {
    var filtered = getFilteredLeads();
    if (filtered.length === 0) {
      showToast("내보낼 리드가 없어요.");
      return;
    }
    var header = ["이름", "연락처", "관심 과정", "유입 경로", "담당자", "상태", "최근 연락일"];
    var rows = filtered.map(function (l) {
      return [l.name, l.phone, l.course, l.source, l.manager, l.status, l.lastContact];
    });
    var csv = [header].concat(rows).map(function (row) {
      return row.map(function (cell) { return '"' + String(cell).replace(/"/g, '""') + '"'; }).join(",");
    }).join("\r\n");
    var blob = new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8;" });
    var url = URL.createObjectURL(blob);
    var a = document.createElement("a");
    a.href = url;
    a.download = "리드목록_" + todayStr().replace(/\./g, "") + ".csv";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showToast(filtered.length + "건을 CSV로 내보냈어요.");
  });

  // ----- ESC로 모달 닫기 -----
  document.addEventListener("keydown", function (e) {
    if (e.key !== "Escape") return;
    if (!addModal.hasAttribute("hidden")) closeAddModal();
    if (!detailModal.hasAttribute("hidden")) closeDetailModal();
  });

  fetch("/api/ai/leads")
    .then(function (res) { return res.json(); })
    .then(function (data) {
      leads = data;
      
      var managerSet = {};
      var sourceSet = {};
      var cohortSet = {};
      
      leads.forEach(function (l) {
        if (l.manager) managerSet[l.manager] = true;
        if (l.source) sourceSet[l.source] = true;
        if (l.className) cohortSet[l.className] = true;
      });
      
      MANAGERS = Object.keys(managerSet).map(function (name) {
        var avatar = "/images/default_avatar_man.png";
        if (name === "이서연" || name === "최민지") {
          avatar = "/images/default_avatar_woman.png";
        } else if (name === "김상담") {
          avatar = "/images/Daon_Kim.png";
        }
        return { name: name, avatar: avatar };
      });
      
      SOURCES = Object.keys(sourceSet);
      COHORTS = ["전체 기수"].concat(Object.keys(cohortSet));
      
      setupDropdowns();
      setupModalSelectOptions();
      
      renderTable();
      renderFlow();
      renderFollowUpList();
    })
    .catch(function (err) {
      console.error("Failed to load leads from server:", err);
      showToast("데이터 로드 중 오류가 발생했습니다.");
    });
})();
