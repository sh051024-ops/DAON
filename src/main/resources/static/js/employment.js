// ============================================================
// 취업 관리 페이지 (employment.html) 전용 스크립트 - API 연동, 페이지네이션 및 AI 매칭
// ============================================================

(function () {
  var graduates = [];
  var stats = {};
  var coursePerformance = [];
  var selectedGradId = null;

  function avatarSrc(gender) {
    return gender === "여" ? "/images/default_avatar_woman.png" : "/images/default_avatar_man.png";
  }

  // 페이지네이션 상태 변수
  var currentPage = 1;
  var pageSize = 10;

  // 상태 배지 클래스 매핑
  var STATUS_BADGES = {
    "취업완료": "badge-green",
    "재직중": "badge-blue",
    "구직중": "badge-amber",
    "미정": "badge-gray"
  };

  // ----- DOM 요소 참조 -----
  var kpiRate = document.getElementById("kpiRate");
  var kpiEmployed = document.getElementById("kpiEmployed");
  var kpiSeekers = document.getElementById("kpiSeekers");
  var kpiTracking = document.getElementById("kpiTracking");

  var gradCount = document.getElementById("gradCount");
  var gradSearchInput = document.getElementById("gradSearchInput");
  var gradStatusFilter = document.getElementById("gradStatusFilter");
  var gradClassFilter = document.getElementById("gradClassFilter");
  var gradTableBody = document.getElementById("gradTableBody");
  var paginationContainer = document.getElementById("paginationContainer");

  var matchingEmpty = document.getElementById("matchingEmpty");
  var matchingForm = document.getElementById("matchingForm");
  var matchIdInput = document.getElementById("matchId");
  var matchAvatar = document.getElementById("matchAvatar");
  var matchName = document.getElementById("matchName");
  var matchClass = document.getElementById("matchClass");
  var matchStatusSelect = document.getElementById("matchStatusSelect");
  var matchCompanyInput = document.getElementById("matchCompanyInput");
  var matchEmployedAtInput = document.getElementById("matchEmployedAtInput");
  var matchTrackUntilInput = document.getElementById("matchTrackUntilInput");

  // AI 매칭 연동 요소
  var aiMatchBtn = document.getElementById("aiMatchBtn");
  var aiMatchingResultArea = document.getElementById("aiMatchingResultArea");
  var aiMatchLoader = document.getElementById("aiMatchLoader");
  var aiMatchCards = document.getElementById("aiMatchCards");

  var chartContainer = document.getElementById("chartContainer");
  var toast = document.getElementById("toast");

  // HTML 이스케이프
  function escapeHtml(str) {
    return String(str || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  // 오늘 날짜 YYYY.MM.DD 형식
  function getTodayStr() {
    var d = new Date();
    return d.getFullYear() + "." + String(d.getMonth() + 1).padStart(2, "0") + "." + String(d.getDate()).padStart(2, "0");
  }

  // 토스트 메시지
  var toastTimer = null;
  function showToast(msg) {
    toast.textContent = msg;
    toast.classList.add("is-visible");
    if (toastTimer) clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      toast.classList.remove("is-visible");
    }, 2400);
  }

  // ----- 전체 지표 카드 재계산 및 렌더링 -----
  function recalculateStats() {
    var total = graduates.length;
    var employed = 0;
    var seekers = 0;
    var tracking = 0;

    var classTotal = {};
    var classEmployed = {};

    graduates.forEach(function (g) {
      var isEmp = g.employmentStatus === "취업완료" || g.employmentStatus === "재직중";
      if (isEmp) employed++;
      else if (g.employmentStatus === "구직중") seekers++;

      if (g.trackUntil && g.trackUntil.trim() !== "") tracking++;

      var cls = g.className || "기타";
      classTotal[cls] = (classTotal[cls] || 0) + 1;
      if (isEmp) {
        classEmployed[cls] = (classEmployed[cls] || 0) + 1;
      }
    });

    var rate = total > 0 ? ((employed / total) * 100).toFixed(1) : "0.0";

    kpiRate.textContent = rate;
    kpiEmployed.textContent = employed;
    kpiSeekers.textContent = seekers;
    kpiTracking.textContent = tracking;

    var sortedClasses = Object.keys(classTotal).sort();
    var chartHtml = sortedClasses.map(function (cls) {
      var tot = classTotal[cls];
      var emp = classEmployed[cls] || 0;
      var clsRate = tot > 0 ? ((emp / tot) * 100).toFixed(1) : "0.0";

      return (
        '<div class="chart-item">' +
          '<div class="chart-label">' +
            '<span>' + escapeHtml(cls) + ' (' + emp + '/' + tot + '명)</span>' +
            '<span>' + clsRate + '%</span>' +
          '</div>' +
          '<div class="chart-bar-bg">' +
            '<div class="chart-bar-fill" style="width: ' + clsRate + '%;"></div>' +
          '</div>' +
        '</div>'
      );
    }).join("");

    chartContainer.innerHTML = chartHtml || '<div style="color: var(--text-400); text-align: center; font-size: 13px;">과정별 데이터가 존재하지 않습니다.</div>';
  }

  // ----- 테이블 및 페이지네이션 렌더링 -----
  function renderTable() {
    var query = gradSearchInput.value.trim().toLowerCase();
    var statusVal = gradStatusFilter.value;
    var classVal = gradClassFilter.value;

    var filtered = graduates.filter(function (g) {
      if (statusVal !== "전체" && g.employmentStatus !== statusVal) return false;
      if (classVal !== "전체" && g.className !== classVal) return false;
      if (query) {
        var name = g.name.toLowerCase();
        return name.indexOf(query) !== -1;
      }
      return true;
    });

    gradCount.textContent = filtered.length;

    var totalPages = Math.ceil(filtered.length / pageSize) || 1;
    if (currentPage > totalPages) currentPage = totalPages;
    if (currentPage < 1) currentPage = 1;

    var startIdx = (currentPage - 1) * pageSize;
    var pageItems = filtered.slice(startIdx, startIdx + pageSize);

    if (pageItems.length === 0) {
      gradTableBody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--text-400); padding: 32px 0;">조건에 일치하는 수료생이 없습니다.</td></tr>';
      paginationContainer.innerHTML = "";
      return;
    }

    var html = pageItems.map(function (g) {
      var isSelected = String(g.id) === String(selectedGradId);
      var isActiveClass = isSelected ? 'class="active"' : '';
      var badgeClass = STATUS_BADGES[g.employmentStatus] || "badge-gray";
      
      return (
        '<tr ' + isActiveClass + ' data-id="' + g.id + '">' +
          '<td style="font-weight: 700; color: var(--text-900);">' + escapeHtml(g.name) + '</td>' +
          '<td>' + escapeHtml(g.className) + '</td>' +
          '<td>' + escapeHtml(g.classEnd) + '</td>' +
          '<td><span class="badge ' + badgeClass + '">' + escapeHtml(g.employmentStatus) + '</span></td>' +
          '<td>' + escapeHtml(g.company || "-") + '</td>' +
          '<td style="color: var(--text-500);">' + escapeHtml(g.trackUntil || "-") + '</td>' +
        '</tr>'
      );
    }).join("");

    gradTableBody.innerHTML = html;

    renderPagination(totalPages);
  }

  // ----- 페이지네이션 버튼 그리기 -----
  function renderPagination(totalPages) {
    if (totalPages <= 1) {
      paginationContainer.innerHTML = "";
      return;
    }

    var buttons = [];
    var prevDisabled = currentPage === 1 ? "disabled" : "";
    buttons.push('<button class="page-btn" data-page="' + (currentPage - 1) + '" ' + prevDisabled + '>이전</button>');

    var startPage = Math.max(1, currentPage - 2);
    var endPage = Math.min(totalPages, startPage + 4);
    if (endPage - startPage < 4) {
      startPage = Math.max(1, endPage - 4);
    }

    for (var i = startPage; i <= endPage; i++) {
      var activeClass = i === currentPage ? "active" : "";
      buttons.push('<button class="page-btn ' + activeClass + '" data-page="' + i + '">' + i + '</button>');
    }

    var nextDisabled = currentPage === totalPages ? "disabled" : "";
    buttons.push('<button class="page-btn" data-page="' + (currentPage + 1) + '" ' + nextDisabled + '>다음</button>');

    paginationContainer.innerHTML = buttons.join("");
  }

  // ----- 상세 매칭 매퍼 로드 -----
  function openGradDetail(id) {
    selectedGradId = String(id);
    var grad = graduates.filter(function (g) { return String(g.id) === String(id); })[0];
    if (!grad) return;

    renderTable();

    matchingEmpty.style.display = "none";
    matchingForm.style.display = "block";

    // AI 버튼 켜기 및 기존 추천 결과 숨김 초기화
    aiMatchBtn.style.display = "flex";
    aiMatchingResultArea.style.display = "none";
    aiMatchCards.innerHTML = "";

    matchIdInput.value = grad.id;
    matchName.textContent = grad.name;
    matchClass.textContent = grad.className + " 수료생";
    matchAvatar.querySelector("img").src = avatarSrc(grad.gender);
    matchStatusSelect.value = grad.employmentStatus;
    matchCompanyInput.value = grad.company || "";
    matchEmployedAtInput.value = grad.employedAt || "";
    
    if (!grad.trackUntil || grad.trackUntil.trim() === "") {
      var d = new Date();
      d.setMonth(d.getMonth() + 6);
      matchTrackUntilInput.value = d.getFullYear() + "." + String(d.getMonth() + 1).padStart(2, "0") + "." + String(d.getDate()).padStart(2, "0");
    } else {
      matchTrackUntilInput.value = grad.trackUntil;
    }
  }

  // ----- 이벤트 바인딩 -----

  // 검색 인풋
  gradSearchInput.addEventListener("input", function () {
    currentPage = 1;
    renderTable();
  });

  // 필터들
  gradStatusFilter.addEventListener("change", function () {
    currentPage = 1;
    renderTable();
  });
  gradClassFilter.addEventListener("change", function () {
    currentPage = 1;
    renderTable();
  });

  // 페이지네이션 클릭 이벤트 위임
  paginationContainer.addEventListener("click", function (e) {
    var btn = e.target.closest(".page-btn");
    if (!btn || btn.disabled) return;
    var targetPage = parseInt(btn.getAttribute("data-page"), 10);
    if (!isNaN(targetPage)) {
      currentPage = targetPage;
      renderTable();
    }
  });

  // 테이블 클릭
  gradTableBody.addEventListener("click", function (e) {
    var tr = e.target.closest("tr");
    if (!tr) return;
    var id = tr.getAttribute("data-id");
    if (id) openGradDetail(id);
  });

  // 🤖 AI 추천 기업 매칭 클릭
  aiMatchBtn.addEventListener("click", function () {
    if (!selectedGradId) return;

    // UI 로딩 표시 활성화
    aiMatchingResultArea.style.display = "block";
    aiMatchLoader.style.display = "block";
    aiMatchCards.style.display = "none";
    aiMatchBtn.disabled = true;

    fetch("/api/ai/match-jobs", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id: selectedGradId })
    })
      .then(function (res) { return res.json(); })
      .then(function (data) {
        aiMatchLoader.style.display = "none";
        aiMatchCards.style.display = "flex";
        aiMatchBtn.disabled = false;

        if (!data || data.length === 0) {
          aiMatchCards.innerHTML = '<div style="color: var(--text-400); font-size: 12.5px; text-align: center; padding: 12px 0;">AI 분석 추천 결과가 없습니다.</div>';
          return;
        }

        var html = data.map(function (item) {
          return (
            '<div class="ai-rec-card">' +
              '<div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">' +
                '<div>' +
                  '<span style="font-weight: 700; font-size: 14px; color: var(--text-900);">' + escapeHtml(item.companyName) + '</span> ' +
                  '<span style="font-size: 12px; color: var(--text-500);">| ' + escapeHtml(item.jobTitle) + '</span>' +
                '</div>' +
                '<span style="font-size: 11px; font-weight: 700; padding: 2px 8px; border-radius: 20px; background-color: var(--green-bg); color: var(--green);">' +
                  '적합도 ' + item.matchScore + '%' +
                '</span>' +
              '</div>' +
              '<p style="font-size: 12.5px; color: var(--text-600); margin: 0 0 10px 0; line-height: 1.45;">' +
                escapeHtml(item.reason) +
              '</p>' +
              '<div style="background-color: var(--surface); border: 1px solid var(--border); border-radius: 6px; padding: 10px; font-size: 11.5px; color: var(--text-500); margin-bottom: 12px; line-height: 1.4;">' +
                '<span style="font-weight: 700; color: var(--blue); margin-right: 4px;">합격 팁:</span>' + escapeHtml(item.tip) +
              '</div>' +
              '<button type="button" class="btn-primary apply-ai-match-btn" data-company="' + escapeHtml(item.companyName) + '" style="padding: 7px 12px; font-size: 11.5px; border-radius: 6px; width: 100%; border: none; font-weight: 600; cursor: pointer;">' +
                '이 추천 기업 매칭 적용하기' +
              '</button>' +
            '</div>'
          );
        }).join("");

        aiMatchCards.innerHTML = html;
      })
      .catch(function (err) {
        console.error("Failed to run AI job matching:", err);
        aiMatchLoader.style.display = "none";
        aiMatchBtn.disabled = false;
        showToast("AI 추천 분석 도중 오류가 발생했습니다.");
      });
  });

  // AI 추천 기업 매칭 클릭 이벤트 위임
  aiMatchCards.addEventListener("click", function (e) {
    var btn = e.target.closest(".apply-ai-match-btn");
    if (!btn) return;

    var company = btn.getAttribute("data-company");
    if (company) {
      matchCompanyInput.value = company;
      matchStatusSelect.value = "구직중"; // 매칭 시도 시 구직중으로 변경 유도
      
      // 만약 취업 완료 날짜가 비어 있다면 오늘 날짜 제안 기입
      if (matchEmployedAtInput.value.trim() === "") {
        matchEmployedAtInput.value = getTodayStr();
      }

      showToast("AI 추천 기업 '" + company + "' 매칭 정보가 대입되었습니다. 하단 저장 버튼을 눌러 최종 확정해주세요.");
    }
  });

  // 매칭 상태 저장 서브밋
  matchingForm.addEventListener("submit", function (e) {
    e.preventDefault();
    var id = matchIdInput.value;
    var grad = graduates.filter(function (g) { return String(g.id) === String(id); })[0];
    if (!grad) return;

    var status = matchStatusSelect.value;
    var company = matchCompanyInput.value.trim();
    var employedAt = matchEmployedAtInput.value.trim();
    var trackUntil = matchTrackUntilInput.value.trim();

    fetch("/api/ai/update-employment", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        id: String(id),
        status: status,
        company: company,
        employedAt: employedAt,
        trackUntil: trackUntil
      })
    })
      .then(function (res) { return res.json(); })
      .then(function (resData) {
        if (resData.success) {
          // 로컬 데이터 동기화
          grad.employmentStatus = status;
          grad.company = company;
          grad.employedAt = employedAt;
          grad.trackUntil = trackUntil;

          recalculateStats();
          renderTable();
          showToast(grad.name + " 님의 매칭 정보가 저장되었습니다.");
        } else {
          showToast("저장에 실패했습니다.");
        }
      })
      .catch(function (err) {
        console.error("Failed to update employment matching:", err);
        showToast("서버 통신 오류가 발생했습니다.");
      });
  });

  // ----- 초기 데이터 로드 -----
  function init() {
    fetch("/api/ai/employment")
      .then(function (res) { return res.json(); })
      .then(function (data) {
        graduates = data.graduates || [];
        coursePerformance = data.coursePerformance || [];
        stats = data.stats || {};

        var classNames = {};
        graduates.forEach(function (g) {
          if (g.className) classNames[g.className] = true;
        });

        var options = '<option value="전체">수료과정 전체</option>' + Object.keys(classNames).sort().map(function (c) {
          return '<option value="' + escapeHtml(c) + '">' + escapeHtml(c) + '</option>';
        }).join("");
        gradClassFilter.innerHTML = options;

        recalculateStats();
        renderTable();

        if (graduates.length > 0) {
          openGradDetail(graduates[0].id);
        }
      })
      .catch(function (err) {
        console.error("Failed to fetch employment data:", err);
        showToast("데이터를 불러오는 과정에서 오류가 발생했습니다.");
      });
  }

  setTimeout(init, 100);
})();
