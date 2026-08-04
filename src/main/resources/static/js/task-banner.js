/**
 * AI 추천 업무 배너 스크립트
 * 대시보드에서 AI 추천 업무를 클릭하여 CRM 페이지로 이동한 경우,
 * 해당 페이지 상단에 업무 안내 배너를 표시하고 완료 처리 기능을 제공합니다.
 */
(function () {
  const params = new URLSearchParams(window.location.search);
  const task = params.get("task");
  if (!task) return; // task 파라미터가 없으면 배너를 표시하지 않음

  // 배너가 삽입될 컨테이너 찾기
  const content = document.querySelector(".content");
  if (!content) return;

  // 배너 HTML 생성
  const banner = document.createElement("div");
  banner.id = "ai-task-banner";
  banner.innerHTML = `
    <div class="task-banner-icon">
      <span class="icon i-star-fill" style="width:18px; height:18px; background-color: var(--blue);"></span>
    </div>
    <div class="task-banner-body">
      <div class="task-banner-label">AI 추천 업무</div>
      <div class="task-banner-text">${task}</div>
    </div>
    <button class="task-banner-btn" id="completeTaskBtn">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
      업무 완료
    </button>
  `;

  // 배너를 content 영역의 첫 번째 자식으로 삽입
  content.insertBefore(banner, content.firstChild);

  // 완료 버튼 이벤트 바인딩
  document.getElementById("completeTaskBtn").addEventListener("click", function () {
    const btn = this;
    btn.disabled = true;
    btn.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" style="animation: spin 0.8s linear infinite;"><path d="M21 12a9 9 0 1 1-6.22-8.56"/></svg>
      처리 중...
    `;

    fetch("/api/ai/complete-task", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ task: task })
    })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          // 성공 시 배너를 완료 상태로 변경
          banner.classList.add("task-banner-done");
          banner.innerHTML = `
            <div class="task-banner-icon" style="background: var(--green-bg);">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="var(--green)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
            </div>
            <div class="task-banner-body">
              <div class="task-banner-label" style="color: var(--green);">업무 완료!</div>
              <div class="task-banner-text">처리가 완료되었습니다. 대시보드로 돌아갑니다...</div>
            </div>
          `;

          // 1.5초 후 대시보드로 이동
          setTimeout(() => {
            window.location.href = "/";
          }, 1500);
        } else {
          btn.disabled = false;
          btn.textContent = "업무 완료";
          alert("업무 완료 처리에 실패했습니다: " + data.message);
        }
      })
      .catch(err => {
        console.error("업무 완료 API 호출 실패:", err);
        btn.disabled = false;
        btn.textContent = "업무 완료";
        alert("서버 연결에 실패했습니다. 다시 시도해주세요.");
      });
  });
})();
