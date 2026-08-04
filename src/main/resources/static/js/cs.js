// ============================================================
// 상담 관리 페이지 (cs.html) 전용 스크립트 - 필터링 및 API 연동 버전
// ============================================================

(function () {
  var leads = [];
  var selectedLeadId = null;
  var activeStatus = "전체";
  var activeManager = "전체";

  // 상태 배지 클래스 매핑 (common.css 기준)
  var STATUS_BADGE = {
    "신규": "badge-blue",
    "상담예정": "badge-gray",
    "상담진행": "badge-amber",
    "등록완료": "badge-green",
    "이탈": "badge-red"
  };

  // ----- DOM 요소 참조 -----
  var leadListContainer = document.getElementById("leadListContainer");
  var leadCountEl = document.getElementById("leadCount");
  var leadSearchInput = document.getElementById("leadSearchInput");
  var leadManagerFilter = document.getElementById("leadManagerFilter");
  var miniTabs = document.getElementById("miniTabs");

  var aiStrategyBtn = document.getElementById("aiStrategyBtn");
  var aiStrategyResult = document.getElementById("aiStrategyResult");
  var aiApplyTemplateBtn = document.getElementById("aiApplyTemplateBtn");
  var latestAiTemplate = "";

  var emptyDetailPanel = document.getElementById("emptyDetailPanel");
  var counselingDetailPanel = document.getElementById("counselingDetailPanel");

  var counselingAvatar = document.getElementById("counselingAvatar");
  var counselingName = document.getElementById("counselingName");
  var counselingCourse = document.getElementById("counselingCourse");
  var counselingPhone = document.getElementById("counselingPhone");
  var counselingSource = document.getElementById("counselingSource");
  var counselingManager = document.getElementById("counselingManager");
  var counselingLastContact = document.getElementById("counselingLastContact");
  var counselingStatusSelect = document.getElementById("counselingStatusSelect");

  var timelineContainer = document.getElementById("timelineContainer");
  var newCounselingForm = document.getElementById("newCounselingForm");
  var toast = document.getElementById("toast");

  // 오늘 날짜 문자열 (YYYY.MM.DD)
  function getTodayStr() {
    var d = new Date();
    return d.getFullYear() + "." + String(d.getMonth() + 1).padStart(2, "0") + "." + String(d.getDate()).padStart(2, "0");
  }

  // HTML 이스케이프
  function escapeHtml(str) {
    return String(str || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  // 토스트 메시지 출력
  var toastTimer = null;
  function showToast(msg) {
    toast.textContent = msg;
    toast.classList.add("is-visible");
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toast.classList.remove("is-visible");
    }, 2400);
  }

  // ----- 리드 목록 렌더링 -----
  function renderLeadList() {
    var query = leadSearchInput.value.trim().toLowerCase();
    
    // 필터링 규칙 (검색어 + 미니 상태 탭 + 담당자 필터)
    var filtered = leads.filter(function (l) {
      if (activeStatus !== "전체" && l.status !== activeStatus) return false;
      if (activeManager !== "전체" && l.manager !== activeManager) return false;
      if (query) {
        var name = l.name.toLowerCase();
        var phone = l.phone.replace(/-/g, "");
        return name.indexOf(query) !== -1 || phone.indexOf(query) !== -1;
      }
      return true;
    });

    leadCountEl.textContent = filtered.length;

    if (filtered.length === 0) {
      leadListContainer.innerHTML = '<div style="text-align: center; color: var(--text-400); padding: 32px 0; font-size: 13px;">일치하는 리드가 없습니다.</div>';
      return;
    }

    var html = filtered.map(function (l) {
      var isSelected = String(l.id) === String(selectedLeadId);
      var isActiveClass = isSelected ? "active" : "";
      var badgeClass = STATUS_BADGE[l.status] || "badge-gray";
      
      return (
        '<div class="lead-item-card ' + isActiveClass + '" data-id="' + l.id + '">' +
          '<div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px;">' +
            '<div style="display: flex; align-items: center; gap: 8px;">' +
              '<span style="font-weight: 700; font-size: 14.5px; color: var(--text-900);">' + escapeHtml(l.name) + '</span>' +
              '<span class="badge ' + badgeClass + '" style="font-size: 11px; padding: 2px 6px;">' + escapeHtml(l.status) + '</span>' +
            '</div>' +
            '<span style="font-size: 11.5px; color: var(--text-400);">' + escapeHtml(l.lastContact) + '</span>' +
          '</div>' +
          '<div style="display: flex; align-items: center; justify-content: space-between; font-size: 12.5px; color: var(--text-500);">' +
            '<span>' + escapeHtml(l.course) + '</span>' +
            '<span style="font-weight: 500; color: var(--text-700);">' + escapeHtml(l.manager) + '</span>' +
          '</div>' +
        '</div>'
      );
    }).join("");

    leadListContainer.innerHTML = html;
  }

  // ----- 타임라인 렌더링 -----
  function renderTimeline(history) {
    if (!history || history.length === 0) {
      timelineContainer.innerHTML = '<div style="color: var(--text-400); font-size: 13px; padding: 12px 0;">상담 기록이 아직 없습니다. 아래 폼을 통해 첫 일지를 작성해 주세요.</div>';
      return;
    }

    var html = history.map(function (h) {
      var typeClass = "";
      if (h.type === "대면") typeClass = "background-color: var(--blue-bg); color: var(--blue); border: 1px solid var(--blue);";
      else if (h.type === "전화") typeClass = "background-color: var(--green-bg); color: var(--green); border: 1px solid var(--green);";
      else if (h.type === "카톡") typeClass = "background-color: var(--amber-bg); color: var(--amber); border: 1px solid var(--amber);";
      else typeClass = "background-color: var(--gray-bg); color: var(--text-500); border: 1px solid var(--border);";

      return (
        '<div style="position: relative; margin-bottom: 4px;">' +
          '<span style="position: absolute; left: -26px; top: 4px; width: 10px; height: 10px; border-radius: 50%; background-color: var(--blue); border: 2px solid var(--surface); box-shadow: 0 0 0 2px var(--blue-bg);"></span>' +
          '<div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px;">' +
            '<span style="font-weight: 700; font-size: 13.5px; color: var(--text-900);">' + escapeHtml(h.summary) + '</span>' +
            '<span style="font-size: 11px; padding: 1px 6px; border-radius: 4px; font-weight: 600; ' + typeClass + '">' + escapeHtml(h.type) + '</span>' +
            '<span style="font-size: 11.5px; color: var(--text-400); margin-left: auto;">' + escapeHtml(h.date) + '</span>' +
          '</div>' +
          '<p style="font-size: 13px; color: var(--text-700); margin: 0; line-height: 1.5; background-color: var(--page-bg); padding: 10px 12px; border-radius: 6px; border: 1px solid var(--border);">' +
            escapeHtml(h.memo) +
          '</p>' +
        '</div>'
      );
    }).join("");

    timelineContainer.innerHTML = html;
  }

  // ----- 특정 리드 상세 및 상담 타임라인 API 호출 -----
  function openLeadDetail(id) {
    selectedLeadId = String(id);
    var lead = leads.filter(function (l) { return String(l.id) === String(id); })[0];
    if (!lead) return;

    // 왼쪽 리스트 선택 상태 갱신
    renderLeadList();

    // 오른쪽 상세 영역 켜기
    emptyDetailPanel.style.display = "none";
    counselingDetailPanel.style.display = "block";

    // 프로필 매핑
    counselingAvatar.src = lead.avatar || "/images/default_avatar_man.png";
    counselingName.textContent = lead.name;
    counselingCourse.textContent = lead.course;
    counselingPhone.textContent = lead.phone;
    counselingSource.textContent = lead.source;
    counselingManager.textContent = lead.manager;
    counselingLastContact.textContent = lead.lastContact;
    counselingStatusSelect.value = lead.status;

    // 백엔드 API에서 상담 히스토리 불러오기
    fetch("/api/ai/counseling-history?id=" + encodeURIComponent(id))
      .then(function (res) { return res.json(); })
      .then(function (history) {
        renderTimeline(history);
      })
      .catch(function (err) {
        console.error("Failed to load counseling history:", err);
        showToast("상담 히스토리를 불러오는 데 실패했습니다.");
      });

    newCounselingForm.reset();

    // AI 상담 전략 카드 초기화
    aiStrategyResult.innerHTML = "[분석 실행] 버튼을 누르면 이 리드의 유입 경로 및 관심 과정 데이터를 기반으로 최적의 상담 멘트와 등록 유도 전략을 추천합니다.";
    aiApplyTemplateBtn.style.display = "none";
    aiStrategyBtn.disabled = false;
    aiStrategyBtn.textContent = "분석 실행";
    latestAiTemplate = "";
  }

  // ----- 이벤트 바인딩 -----

  // 실시간 검색
  leadSearchInput.addEventListener("input", function () {
    renderLeadList();
  });

  // 담당자 필터링 변경
  leadManagerFilter.addEventListener("change", function () {
    activeManager = leadManagerFilter.value;
    renderLeadList();
  });

  // 미니 탭 필터링 변경
  miniTabs.addEventListener("click", function (e) {
    var tab = e.target.closest(".mini-tab");
    if (!tab) return;
    miniTabs.querySelectorAll(".mini-tab").forEach(function (t) { t.classList.remove("active"); });
    tab.classList.add("active");
    activeStatus = tab.getAttribute("data-status");
    renderLeadList();
  });

  // 리드 클릭 액션
  leadListContainer.addEventListener("click", function (e) {
    var card = e.target.closest(".lead-item-card");
    if (!card) return;
    var id = card.getAttribute("data-id");
    openLeadDetail(id);
  });

  // 리드 상태 셀렉트 박스 변경 시 백엔드 저장 API 호출 연동
  counselingStatusSelect.addEventListener("change", function () {
    if (selectedLeadId === null) return;
    var lead = leads.filter(function (l) { return String(l.id) === String(selectedLeadId); })[0];
    if (!lead) return;

    var newStatus = counselingStatusSelect.value;
    var today = getTodayStr();

    fetch("/api/ai/update-lead", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: selectedLeadId,
        status: newStatus,
        lastContact: today
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (resData) {
        if (resData.success) {
          // 로컬 데이터 동기화
          lead.status = newStatus;
          lead.lastContact = today;

          renderLeadList();
          counselingLastContact.textContent = today;
          showToast(lead.name + " 님의 상태가 '" + newStatus + "'(으)로 변경되었습니다.");

          // 상태가 변경되었으므로 타임라인 갱신을 위해 상세 정보를 다시 로드
          openLeadDetail(selectedLeadId);
        } else {
          showToast("상태 변경 저장에 실패했습니다.");
        }
      })
      .catch(function (err) {
        console.error("Failed to update lead status:", err);
        showToast("서버와 통신 중 오류가 발생했습니다.");
      });
  });

  // 새 상담 기록 등록 API 연동
  newCounselingForm.addEventListener("submit", function (e) {
    e.preventDefault();
    if (selectedLeadId === null) return;

    var lead = leads.filter(function (l) { return String(l.id) === String(selectedLeadId); })[0];
    if (!lead) return;

    var type = document.getElementById("newCounselingType").value;
    var summary = document.getElementById("newCounselingSummary").value.trim();
    var memo = document.getElementById("newCounselingMemo").value.trim();

    fetch("/api/ai/add-counseling", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: selectedLeadId,
        type: type,
        summary: summary,
        memo: memo
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (resData) {
        if (resData.success) {
          // 로컬 리드 정보 갱신
          lead.lastContact = getTodayStr();
          if (lead.status === "신규") {
            lead.status = "상담진행";
            counselingStatusSelect.value = "상담진행";
          }

          // 화면 갱신
          renderLeadList();
          counselingLastContact.textContent = getTodayStr();

          // 타임라인 새로고침
          openLeadDetail(selectedLeadId);

          newCounselingForm.reset();
          showToast(lead.name + " 님의 상담 일지가 서버에 저장되었습니다.");
        } else {
          showToast("상담 일지 저장에 실패했습니다.");
        }
      })
      .catch(function (err) {
        console.error("Failed to add counseling:", err);
        showToast("서버와 통신 중 오류가 발생했습니다.");
      });
  });

  // AI 상담 전략 실행 버튼 이벤트
  aiStrategyBtn.addEventListener("click", function () {
    if (selectedLeadId === null) return;
    
    aiStrategyBtn.disabled = true;
    aiStrategyBtn.textContent = "분석 중...";
    aiStrategyResult.innerHTML = '<span style="color: var(--text-500);">AI가 상담 전략을 생성하는 중입니다. 잠시만 기다려주세요...</span>';
    
    fetch("/api/ai/counseling-strategy", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id: selectedLeadId })
    })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        aiStrategyBtn.disabled = false;
        aiStrategyBtn.textContent = "재분석";
        
        if (data.strategy && data.template) {
          latestAiTemplate = data.template;
          aiStrategyResult.innerHTML = 
            '<strong>[추천 전략]</strong><br>' + escapeHtml(data.strategy) + '<br><br>' +
            '<strong>[추천 첫인사 템플릿]</strong><br><span style="background-color: #fff; border: 1px solid var(--border); border-radius: 4px; padding: 6px 8px; display: block; margin-top: 4px; font-style: italic;">' + escapeHtml(data.template) + '</span>';
          aiApplyTemplateBtn.style.display = "block";
        } else {
          aiStrategyResult.textContent = "AI 분석 결과를 생성하지 못했습니다.";
        }
      })
      .catch(function (err) {
        console.error("AI counseling strategy error:", err);
        aiStrategyBtn.disabled = false;
        aiStrategyBtn.textContent = "분석 실행";
        aiStrategyResult.textContent = "AI 분석에 실패했습니다. 다시 시도해 주세요.";
      });
  });

  // AI 추천 템플릿 복사 및 입력 폼 대입 이벤트
  aiApplyTemplateBtn.addEventListener("click", function () {
    if (!latestAiTemplate) return;
    document.getElementById("newCounselingSummary").value = "AI 추천 첫 상담 안내";
    document.getElementById("newCounselingMemo").value = latestAiTemplate;
    showToast("AI 추천 템플릿이 새 상담 기록 폼에 입력되었습니다. 확인 후 저장하세요.");
  });

  // ----- 초기 데이터 로딩 -----
  function init() {
    fetch("/api/ai/leads")
      .then(function (res) { return res.json(); })
      .then(function (data) {
        leads = data;
        
        // 담당자 필터 목록 채우기
        var managerSet = {};
        leads.forEach(function (l) {
          if (l.manager) managerSet[l.manager] = true;
        });
        
        var selectHtml = '<option value="전체">담당자 전체</option>' + Object.keys(managerSet).map(function (m) {
          return '<option value="' + escapeHtml(m) + '">' + escapeHtml(m) + '</option>';
        }).join("");
        leadManagerFilter.innerHTML = selectHtml;

        renderLeadList();
        
        // 목록 로드 후 첫 번째 리드 자동 선택 처리
        if (leads.length > 0) {
          openLeadDetail(leads[0].id);
        }
      })
      .catch(function (err) {
        console.error("Failed to load leads for counseling page:", err);
        showToast("데이터를 로드하는 도중 오류가 발생했습니다.");
      });
  }

  setTimeout(init, 100);
})();
