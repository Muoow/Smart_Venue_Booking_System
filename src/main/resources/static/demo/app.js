const screens = ["home", "booking", "booking-detail", "orders", "settings"];
const tabButtons = [...document.querySelectorAll(".tab-btn")];
const screenNodes = [...document.querySelectorAll(".app-screen")];
const query = new URLSearchParams(window.location.search);
const requestedScreen = query.get("screen");
const TOKEN_STORAGE_KEY = "courtflow-demo-token";

const state = {
  token: localStorage.getItem(TOKEN_STORAGE_KEY) || "",
  profile: null,
  venues: [],
  recommendations: [],
  reservations: [],
  ordersFilter: "all",
  authMode: "login",
  bookingSearchKeyword: "",
  bookingTypeFilter: "all",
  selectedVenueId: null,
  selectedResourceId: null,
  availability: null,
  selectedSlots: new Set(),
  selectionAnchorUnit: null,
};

let toastTimer = null;

const els = {
  authText: document.getElementById("auth-text"),
  appToast: document.getElementById("app-toast"),
  appToastContent: document.getElementById("app-toast-content"),
  ordersTitle: document.getElementById("orders-title"),
  welcomeTitle: document.getElementById("welcome-title"),
  welcomeSubtitle: document.getElementById("welcome-subtitle"),
  heroResource: document.getElementById("hero-resource"),
  heroTime: document.getElementById("hero-time"),
  quickMetrics: document.getElementById("quick-metrics"),
  venueList: document.getElementById("venue-list"),
  bookingVenueList: document.getElementById("booking-venue-list"),
  bookingSearch: document.getElementById("booking-search"),
  bookingTypeFilters: document.getElementById("booking-type-filters"),
  recommendationList: document.getElementById("recommendation-list"),
  bookingDate: document.getElementById("booking-date"),
  bookingSize: document.getElementById("booking-size"),
  resourceList: document.getElementById("resource-list"),
  timeGrid: document.getElementById("time-grid"),
  selectedPrice: document.getElementById("selected-price"),
  bookingSummaryPrice: document.getElementById("booking-summary-price"),
  availabilityTip: document.getElementById("availability-tip"),
  summaryResource: document.getElementById("summary-resource"),
  summaryDate: document.getElementById("summary-date"),
  summaryTime: document.getElementById("summary-time"),
  summarySize: document.getElementById("summary-size"),
  slotCount: document.getElementById("slot-count"),
  bookingMessage: document.getElementById("booking-message"),
  bookingActionBar: document.getElementById("booking-action-bar"),
  bookingResourceSection: document.getElementById("booking-resource-section"),
  bookingSettingsSection: document.getElementById("booking-settings-section"),
  bookingSlotsSection: document.getElementById("booking-slots-section"),
  bookingSummarySection: document.getElementById("booking-summary-section"),
  bookingDetailTitle: document.getElementById("booking-detail-title"),
  resourceSectionNote: document.getElementById("resource-section-note"),
  profileCard: document.getElementById("profile-card"),
  profileName: document.getElementById("profile-name"),
  profileDesc: document.getElementById("profile-desc"),
  profileBalance: document.getElementById("profile-balance"),
  profileAvatar: document.getElementById("profile-avatar"),
  guestAvatar: document.getElementById("guest-avatar"),
  profileStats: document.getElementById("profile-stats"),
  ordersSettingsButton: document.getElementById("orders-settings-btn"),
  settingsUsername: document.getElementById("settings-username"),
  settingsCurrentPassword: document.getElementById("settings-current-password"),
  settingsNewPassword: document.getElementById("settings-new-password"),
  settingsConfirmPassword: document.getElementById("settings-confirm-password"),
  settingsLogoutButton: document.getElementById("settings-logout-btn"),
  settingsBackButton: document.getElementById("settings-back-btn"),
  orderList: document.getElementById("order-list"),
  homeAvatar: document.getElementById("home-avatar"),
  bookingAvatar: document.getElementById("booking-avatar"),
  bookingDetailAvatar: document.getElementById("booking-detail-avatar"),
  heroBookingAction: document.getElementById("hero-booking-action"),
  ordersGuestPanel: document.getElementById("orders-guest-panel"),
  ordersMemberPanel: document.getElementById("orders-member-panel"),
  submitBookingButton: document.getElementById("submit-booking"),
  authLoginTab: document.getElementById("auth-login-tab"),
  authRegisterTab: document.getElementById("auth-register-tab"),
  authUsername: document.getElementById("auth-username"),
  authNicknameField: document.getElementById("auth-nickname-field"),
  authNickname: document.getElementById("auth-nickname"),
  authPassword: document.getElementById("auth-password"),
  authConfirmPasswordField: document.getElementById("auth-confirm-password-field"),
  authConfirmPassword: document.getElementById("auth-confirm-password"),
  authSubmitButton: document.getElementById("auth-submit-btn"),
  authMessage: document.getElementById("auth-message"),
  settingsNickname: document.getElementById("settings-nickname"),
  settingsProfileSaveButton: document.getElementById("settings-profile-save-btn"),
  settingsProfileMessage: document.getElementById("settings-profile-message"),
  settingsPasswordSaveButton: document.getElementById("settings-password-save-btn"),
  settingsPasswordMessage: document.getElementById("settings-password-message"),
};

function setActiveScreen(screenKey) {
  tabButtons.forEach((button) => {
    const isBookingTab = button.dataset.screen === "booking" && (screenKey === "booking" || screenKey === "booking-detail");
    const isOrdersTab = button.dataset.screen === "orders" && (screenKey === "orders" || screenKey === "settings");
    button.classList.toggle("active", isBookingTab || isOrdersTab || button.dataset.screen === screenKey);
  });
  screenNodes.forEach((node) => {
    node.classList.toggle("active", node.id === `screen-${screenKey}`);
  });
  els.bookingActionBar.hidden = screenKey !== "booking-detail" || !state.selectedVenueId;
}

function markPageReady() {
  document.body.dataset.ready = "true";
}

function buildSlotOptions(start, end, stepMinutes) {
  const result = [];
  let current = toMinutes(start);
  const endMinutes = toMinutes(end);
  while (current <= endMinutes) {
    result.push({
      label: formatMinutes(current),
      unit: Math.floor(current / 10),
    });
    current += stepMinutes;
  }
  return result;
}

function toMinutes(value) {
  const [hour, minute] = value.split(":").map(Number);
  return hour * 60 + minute;
}

function formatMinutes(totalMinutes) {
  const hour = String(Math.floor(totalMinutes / 60)).padStart(2, "0");
  const minute = String(totalMinutes % 60).padStart(2, "0");
  return `${hour}:${minute}`;
}

function formatDate(input) {
  if (!input) {
    return "未选择";
  }
  const date = new Date(input);
  if (Number.isNaN(date.getTime())) {
    return input;
  }
  return date.toLocaleDateString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    weekday: "short",
  });
}

function formatSlotRange(slotUnits) {
  if (!slotUnits.length) {
    return "未选择";
  }
  const sorted = [...slotUnits].sort((a, b) => a - b);
  const unitMinutes = getSelectedResource()?.resource?.unitMinutes || state.availability?.unitMinutes || 10;
  const start = formatMinutes(sorted[0] * unitMinutes);
  const end = formatMinutes((sorted[sorted.length - 1] + 1) * unitMinutes);
  return `${start} - ${end}`;
}

function formatInputDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function getTodayDateString() {
  return formatInputDate(new Date());
}

function getMaxBookingDateString() {
  const maxDate = new Date();
  maxDate.setDate(maxDate.getDate() + 14);
  return formatInputDate(maxDate);
}

function isPastBookingDate(dateValue) {
  return Boolean(dateValue) && dateValue < getTodayDateString();
}

function isTodayBookingDate(dateValue) {
  return Boolean(dateValue) && dateValue === getTodayDateString();
}

function isBeyondBookingWindow(dateValue) {
  return Boolean(dateValue) && dateValue > getMaxBookingDateString();
}

function getCurrentMinutesOfDay() {
  const now = new Date();
  return now.getHours() * 60 + now.getMinutes();
}

function hideToast() {
  if (toastTimer) {
    clearTimeout(toastTimer);
    toastTimer = null;
  }
  els.appToast.hidden = true;
  els.appToast.classList.remove("active");
}

function showToast(message, duration = 3200) {
  if (!message) {
    hideToast();
    return;
  }
  if (toastTimer) {
    clearTimeout(toastTimer);
  }
  els.appToastContent.textContent = message;
  els.appToast.hidden = false;
  requestAnimationFrame(() => {
    els.appToast.classList.add("active");
  });
  toastTimer = window.setTimeout(() => {
    els.appToast.classList.remove("active");
    toastTimer = window.setTimeout(() => {
      els.appToast.hidden = true;
      toastTimer = null;
    }, 240);
  }, duration);
}

function showTransientBookingMessage(message, duration = 3200) {
  showToast(message, duration);
}

function setAuthMode(mode) {
  state.authMode = mode === "register" ? "register" : "login";
  const isRegister = state.authMode === "register";
  els.authLoginTab.classList.toggle("active", !isRegister);
  els.authRegisterTab.classList.toggle("active", isRegister);
  els.authNicknameField.hidden = !isRegister;
  els.authConfirmPasswordField.hidden = !isRegister;
  els.authNickname.disabled = !isRegister;
  els.authConfirmPassword.disabled = !isRegister;
  if (!isRegister) {
    els.authNickname.value = "";
    els.authConfirmPassword.value = "";
  }
  els.authSubmitButton.textContent = isRegister ? "注册并登录" : "登录";
  els.authMessage.textContent = isRegister
    ? "注册时需要填写用户名、用户昵称和密码，注册后会自动登录。"
    : "登录只需要输入用户名和密码。";
}

function resetAuthForm() {
  els.authUsername.value = "";
  els.authNickname.value = "";
  els.authPassword.value = "";
  els.authConfirmPassword.value = "";
  setAuthMode(state.authMode);
}

function getDisplayName() {
  const candidates = [
    state.profile?.nickname?.trim(),
    state.profile?.username?.trim(),
  ];
  return candidates.find((value) => value && !/^\?+$/.test(value)) || "";
}

function getAvatarText() {
  const name = getDisplayName();
  if (!name) {
    return "C";
  }
  return name.charAt(0).toUpperCase();
}

function getRoleLabel(role) {
  if (role === "ADMIN") {
    return "管理员";
  }
  return "用户";
}

function getSelectedVenue() {
  return state.venues.find((venue) => venue.id === state.selectedVenueId) || null;
}

function getFilteredBookingVenues() {
  const keyword = state.bookingSearchKeyword.trim().toLowerCase();
  return state.venues.filter((venue) => {
    const matchType = state.bookingTypeFilter === "all" || venue.type.includes(state.bookingTypeFilter);
    const matchKeyword = !keyword || venue.name.toLowerCase().includes(keyword);
    return matchType && matchKeyword;
  });
}

function getSelectedResource() {
  const venue = getSelectedVenue();
  if (!venue) {
    return null;
  }
  const resource = venue.resources.find((item) => item.id === state.selectedResourceId);
  return resource ? { venue, resource } : null;
}

function renderBookingFlowState() {
  const hasVenue = Boolean(state.selectedVenueId);
  const venue = getSelectedVenue();
  els.bookingDetailTitle.textContent = venue ? `${venue.name} 预约` : "完成预约";
  if (!hasVenue && document.getElementById("screen-booking-detail").classList.contains("active")) {
    setActiveScreen("booking");
  }
}

function getResourceMeta(resourceId) {
  for (const venue of state.venues) {
    const resource = venue.resources.find((item) => item.id === resourceId);
    if (resource) {
      return {
        venueName: venue.name,
        resourceName: resource.name,
      };
    }
  }
  return {
    venueName: "",
    resourceName: "",
  };
}

function renderOrderFilters() {
  document.querySelectorAll(".filter-chip[data-filter]").forEach((button) => {
    button.classList.toggle("active", button.dataset.filter === state.ordersFilter);
  });
}

function getFilteredReservations() {
  if (state.ordersFilter === "active") {
    return state.reservations.filter((item) => item.status === 0 || item.status === 1);
  }
  if (state.ordersFilter === "done") {
    return state.reservations.filter((item) => item.status === 4);
  }
  if (state.ordersFilter === "cancelled") {
    return state.reservations.filter((item) => item.status === 2);
  }
  return state.reservations;
}

async function api(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };
  if (state.token) {
    headers.Authorization = `Bearer ${state.token}`;
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  const data = await response.json().catch(() => ({
    code: response.status,
    message: "接口返回内容无法解析",
  }));

  if (!response.ok || data.code !== 200) {
    const error = new Error(data.message || "请求失败");
    error.status = response.status;
    throw error;
  }

  return data.data;
}

async function loginUser(username, password, successMessage = "") {
  try {
    const token = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username,
        password,
      }),
    });
    state.token = token;
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    els.authText.textContent = "已登录";
    await loadData();
    if (successMessage) {
      showToast(successMessage);
    }
    if (requestedScreen && screens.includes(requestedScreen)) {
      setActiveScreen(requestedScreen);
    } else {
      setActiveScreen("orders");
    }
  } catch (error) {
    throw error;
  }
}

async function submitAuthForm() {
  const username = els.authUsername.value.trim();
  const nickname = els.authNickname.value.trim();
  const password = els.authPassword.value;
  const confirmPassword = els.authConfirmPassword.value;
  const isRegister = state.authMode === "register";

  if (!username) {
    els.authMessage.textContent = "请输入用户名。";
    return;
  }
  if (isRegister && !nickname) {
    els.authMessage.textContent = "请输入用户昵称。";
    return;
  }
  if (!password.trim()) {
    els.authMessage.textContent = "请输入密码。";
    return;
  }
  if (password.length < 5) {
    els.authMessage.textContent = "密码至少 5 位。";
    return;
  }
  if (isRegister && password !== confirmPassword) {
    els.authMessage.textContent = "两次输入的密码不一致。";
    return;
  }

  els.authSubmitButton.disabled = true;
  els.authMessage.textContent = isRegister ? "注册中..." : "登录中...";
  try {
    if (isRegister) {
      await api("/auth/register", {
        method: "POST",
        body: JSON.stringify({
          username,
          nickname,
          password,
        }),
      });
      await loginUser(username, password, "注册成功，已自动登录。");
    } else {
      await loginUser(username, password, "登录成功。");
    }
    resetAuthForm();
  } catch (error) {
    els.authMessage.textContent = error.message;
  } finally {
    els.authSubmitButton.disabled = false;
  }
}

function logoutUser() {
  state.token = "";
  state.profile = null;
  state.reservations = [];
  localStorage.removeItem(TOKEN_STORAGE_KEY);
  window.location.href = window.location.pathname;
}

function renderAuthChrome() {
  const avatarText = getAvatarText();
  els.homeAvatar.textContent = avatarText;
  els.bookingAvatar.textContent = avatarText;
  els.bookingDetailAvatar.textContent = avatarText;
  els.profileAvatar.textContent = avatarText;
  els.guestAvatar.textContent = avatarText;
  els.authText.textContent = state.profile ? getDisplayName() : "CourtFlow";
  els.ordersTitle.textContent = state.token ? "我的预约" : "我的";
  els.heroBookingAction.textContent = "立即预约";
  els.profileCard.hidden = !state.token;
  els.ordersSettingsButton.hidden = !state.token;
  els.ordersGuestPanel.hidden = Boolean(state.token);
  els.ordersMemberPanel.hidden = !state.token;
  els.profileCard.style.display = state.token ? "block" : "none";
  els.ordersGuestPanel.style.display = state.token ? "none" : "grid";
  els.ordersMemberPanel.style.display = state.token ? "block" : "none";
  if (!state.token && document.getElementById("screen-settings").classList.contains("active")) {
    setActiveScreen("orders");
  }
  if (!state.token) {
    setAuthMode(state.authMode);
  }
  populateSettingsForm();
  renderBookingActionState();
}

async function loadData() {
  try {
    const [venues, recommendations] = await Promise.all([
      api("/venue/list"),
      api("/recommendation/venues", {
        method: "POST",
        body: JSON.stringify({
          sportKeyword: "羽毛球",
          preferredUnitMinutes: 10,
          expectedPeopleCount: 4,
          maxBudget: 60,
          preferLowPrice: true,
          expectedStartUnit: 114,
          expectedEndUnit: 125,
          topN: 1,
        }),
      }),
    ]);

    state.venues = venues;
    state.recommendations = recommendations;
    if (state.selectedVenueId && !venues.some((venue) => venue.id === state.selectedVenueId)) {
      state.selectedVenueId = null;
    }
    syncSelectedResource();

    if (state.token) {
      const [profile, reservations] = await Promise.all([
        api("/user/profile"),
        api("/reservation/my?pageNumber=1&pageSize=10"),
      ]);
      state.profile = profile;
      state.reservations = reservations.records || [];
      els.welcomeTitle.textContent = `${getDisplayName() || profile.username}，今天想预约哪块场地？`;
      els.welcomeSubtitle.textContent = `当前可查看 ${venues.length} 个场馆、${reservations.total || state.reservations.length} 条预约记录`;
    } else {
      els.welcomeTitle.textContent = "选择今天想去的场地";
      els.welcomeSubtitle.textContent = `当前开放 ${venues.length} 个场馆，可先浏览场地信息`;
    }

    await loadAvailability(true);
    renderAll();
    renderAuthChrome();
    markPageReady();
  } catch (error) {
    els.welcomeSubtitle.textContent = error.message;
    els.bookingMessage.textContent = error.message;
    renderErrorBlocks(error.message);
    markPageReady();
  }
}

function renderAll() {
  renderBookingFlowState();
  renderMetrics();
  renderVenues();
  renderBookingVenues();
  renderRecommendations();
  renderResources();
  renderSlots();
  renderBookingSummary();
  renderProfile();
  renderOrders();
}

function renderErrorBlocks(message) {
  els.venueList.innerHTML = `<div class="empty-state">${message}</div>`;
  els.bookingVenueList.innerHTML = `<div class="empty-state">${message}</div>`;
  els.recommendationList.innerHTML = `<div class="empty-state">${message}</div>`;
  els.resourceList.innerHTML = `<div class="empty-state">${message}</div>`;
  els.orderList.innerHTML = `<div class="empty-state">${message}</div>`;
  els.timeGrid.innerHTML = `<div class="empty-state">${message}</div>`;
}

function renderMetrics() {
  const totalResources = state.venues.reduce((sum, venue) => sum + venue.resourceCount, 0);
  const metrics = [
    {
      label: "开放场馆",
      value: `${state.venues.length}`,
    },
    {
      label: "可约场地",
      value: `${totalResources}`,
    },
    {
      label: "预约记录",
      value: `${state.reservations.length}`,
    },
  ];

  els.quickMetrics.innerHTML = metrics.map((item) => `
    <article class="metric-card">
      <strong>${item.value}</strong>
      <span>${item.label}</span>
    </article>
  `).join("");
}

function renderVenues() {
  if (!state.venues.length) {
    els.venueList.innerHTML = '<div class="empty-state">当前没有可用场馆数据。</div>';
    return;
  }

  els.venueList.innerHTML = state.venues.map((venue) => {
    const resource = venue.resources[0];
    return `
      <article class="venue-card interactive-card" data-home-venue-id="${venue.id}">
        <div class="venue-body">
          <div class="venue-title">
            <strong>${venue.name}</strong>
            <span>${venue.resourceCount} 个场地</span>
          </div>
          <p>${venue.type} · ${resource ? resource.unitMinutes : "--"} 分钟/段</p>
          <div class="venue-meta">
            <span>${resource ? resource.name : "暂无场地"}</span>
            <span>${resource ? `¥${resource.price}` : "待定"}</span>
          </div>
        </div>
        <div class="card-link">去预约</div>
      </article>
    `;
  }).join("");

  [...els.venueList.querySelectorAll("[data-home-venue-id]")].forEach((node) => {
    node.addEventListener("click", async () => {
      if (!state.token) {
        setActiveScreen("orders");
        return;
      }
      await selectVenue(Number(node.dataset.homeVenueId));
      setActiveScreen("booking-detail");
    });
  });
}

function renderRecommendations() {
  if (!state.recommendations.length) {
    els.recommendationList.innerHTML = '<div class="empty-state hero-empty-state">暂无推荐结果。</div>';
    els.heroResource.textContent = "猜你喜欢";
    els.heroTime.textContent = "可以稍后刷新推荐或直接进入预约页选择场地。";
    return;
  }

  const top = state.recommendations[0];
  els.heroResource.textContent = "猜你喜欢";
  els.heroTime.textContent = top.reasonList.join("；");

  els.recommendationList.innerHTML = state.recommendations.slice(0, 1).map((item) => `
    <article class="hero-recommendation-card" data-recommend-venue-name="${item.venueName}">
      <div class="hero-recommendation-head">
        <strong>${item.venueName}</strong>
        <span>${item.resourceName}</span>
      </div>
      <p>${item.reasonList.join("；")}</p>
    </article>
  `).join("");

  [...els.recommendationList.querySelectorAll("[data-recommend-venue-name]")].forEach((node) => {
    node.addEventListener("click", async () => {
      const venue = state.venues.find((item) => item.name === node.dataset.recommendVenueName);
      if (!venue) {
        return;
      }
      if (!state.token) {
        setActiveScreen("orders");
        return;
      }
      await selectVenue(venue.id);
      setActiveScreen("booking-detail");
    });
  });
}

function renderResources() {
  const venue = getSelectedVenue();
  if (!venue) {
    els.resourceSectionNote.textContent = "请先选择场馆";
    els.resourceList.innerHTML = '<div class="empty-state">选择场馆后再选择具体场地。</div>';
    return;
  }
  const options = venue.resources.map((resource) => ({ venue, resource }));
  els.resourceSectionNote.textContent = `${venue.name} · 请选择具体场地`;

  if (!options.length) {
    els.resourceList.innerHTML = '<div class="empty-state">当前场馆暂无可预约场地。</div>';
    return;
  }

  els.resourceList.innerHTML = options.map(({ venue, resource }) => `
    <article class="resource-option ${resource.id === state.selectedResourceId ? "active" : ""}" data-resource-id="${resource.id}">
      <strong>${venue.name} · ${resource.name}</strong>
      <p>${resource.capacity} 人 · ${resource.unitMinutes} 分钟/段 · ¥${resource.price}</p>
    </article>
  `).join("");

  [...els.resourceList.querySelectorAll("[data-resource-id]")].forEach((node) => {
    node.addEventListener("click", async () => {
      state.selectedResourceId = Number(node.dataset.resourceId);
      resetSlotSelection();
      await loadAvailability(true);
      renderResources();
      renderSlots();
      renderBookingSummary();
    });
  });
}

function renderBookingVenues() {
  renderBookingTypeFilters();
  const venues = getFilteredBookingVenues();
  if (!venues.length) {
    const message = state.venues.length ? "没有找到符合条件的场馆。" : "暂无可预约场馆。";
    els.bookingVenueList.innerHTML = `<div class="empty-state">${message}</div>`;
    return;
  }
  if (!state.venues.length) {
    els.bookingVenueList.innerHTML = '<div class="empty-state">暂无可预约场馆。</div>';
    return;
  }
  els.bookingVenueList.innerHTML = venues.map((venue) => `
    <article class="venue-card interactive-card ${venue.id === state.selectedVenueId ? "active" : ""}" data-booking-venue-id="${venue.id}">
      <div class="venue-body">
        <div class="venue-title">
          <strong>${venue.name}</strong>
          <span>${venue.resourceCount} 个场地</span>
        </div>
        <p>${venue.type}</p>
      </div>
      <div class="card-link">${venue.id === state.selectedVenueId ? "已选择" : "选择场馆"}</div>
    </article>
  `).join("");

  [...els.bookingVenueList.querySelectorAll("[data-booking-venue-id]")].forEach((node) => {
    node.addEventListener("click", async () => {
      if (!state.token) {
        setActiveScreen("orders");
        return;
      }
      await selectVenue(Number(node.dataset.bookingVenueId));
    });
  });
}

function renderBookingTypeFilters() {
  const uniqueTypes = [...new Set(state.venues.map((venue) => venue.type))];
  const filters = [
    { key: "all", label: "全部" },
    ...uniqueTypes.map((type) => ({ key: type, label: type })),
  ];
  els.bookingTypeFilters.innerHTML = filters.map((item) => `
    <button class="filter-chip ${state.bookingTypeFilter === item.key ? "active" : ""}" data-booking-filter="${item.key}" type="button">${item.label}</button>
  `).join("");
  [...els.bookingTypeFilters.querySelectorAll("[data-booking-filter]")].forEach((button) => {
    button.addEventListener("click", () => {
      state.bookingTypeFilter = button.dataset.bookingFilter;
      renderBookingVenues();
    });
  });
}

function renderSlots() {
  const slots = getVisibleAvailabilitySlots();
  ensureBookingDateIsValid();
  if (!state.selectedVenueId) {
    els.availabilityTip.textContent = "请先选择场馆。";
    els.timeGrid.innerHTML = '<div class="empty-state">先选择场馆，再继续选择场地、人数和时段。</div>';
    return;
  }
  if (!state.selectedResourceId) {
    els.availabilityTip.textContent = "请先选择具体场地。";
    els.timeGrid.innerHTML = '<div class="empty-state">选择场地后加载时段。</div>';
    return;
  }
  if (!els.bookingDate.value) {
    els.availabilityTip.textContent = "请先选择预约日期。";
    els.timeGrid.innerHTML = '<div class="empty-state">选择日期后加载时段。</div>';
    return;
  }
  if (!state.token) {
    els.availabilityTip.textContent = "请登录后查看可约时段。";
    els.timeGrid.innerHTML = '<div class="empty-state">请登录后查看</div>';
    return;
  }
  if (!slots.length) {
    els.availabilityTip.textContent = "当前日期暂无可用时段数据。";
    els.timeGrid.innerHTML = '<div class="empty-state">暂无可展示时段。</div>';
    return;
  }

  const selected = getSelectedResource();
  const todayHint = isTodayBookingDate(els.bookingDate.value) ? " · 今天已自动过滤当前时刻之前的时段" : "";
  els.availabilityTip.textContent = `${selected?.resource?.name || "当前场地"} · ${els.bookingDate.value} · 点击一次选起点，再点一次选终点${todayHint}`;
  els.timeGrid.innerHTML = slots.map((slot) => {
    const isSelected = state.selectedSlots.has(slot.slotUnit);
    const isBusy = !isSlotAvailableForSize(slot);
    return `
      <button class="time-slot ${isSelected ? "selected" : ""} ${isBusy ? "busy" : ""}" data-unit="${slot.slotUnit}" ${isBusy ? "disabled" : ""}>
        <span>${slot.startTime}</span>
        <small>${getSlotStateText(slot, isBusy)}</small>
      </button>
    `;
  }).join("");

  [...els.timeGrid.querySelectorAll("[data-unit]")].forEach((button) => {
    if (button.disabled) {
      return;
    }
    button.addEventListener("click", () => {
      const unit = Number(button.dataset.unit);
      pickSlotUnit(unit);
      renderSlots();
      renderBookingSummary();
    });
  });
}

function renderBookingSummary() {
  const selected = getSelectedResource();
  const sortedUnits = [...state.selectedSlots].sort((a, b) => a - b);
  const price = selected ? sortedUnits.length * selected.resource.price : 0;

  els.selectedPrice.textContent = `¥${selected ? selected.resource.price : 0}`;
  els.bookingSummaryPrice.textContent = `¥${price}`;
  els.summaryResource.textContent = selected ? `${selected.venue.name} · ${selected.resource.name}` : "未选择";
  els.summaryDate.textContent = formatDate(els.bookingDate.value);
  els.summaryTime.textContent = formatSlotRange(sortedUnits);
  els.summarySize.textContent = `${els.bookingSize.value || 1} 人`;
  els.slotCount.textContent = `已选择 ${sortedUnits.length} 个时间段`;
  if (!state.selectedVenueId) {
    els.bookingMessage.textContent = "请先选择场馆。";
  } else if (!sortedUnits.length) {
    els.bookingMessage.textContent = "先选择场馆与场地，再选择日期和连续时间段。";
  }
  renderBookingActionState();
}

function renderBookingActionState() {
  const selected = getSelectedResource();
  const hasDate = Boolean(els.bookingDate.value);
  const hasSlots = state.selectedSlots.size > 0;
  const hasVenue = Boolean(state.selectedVenueId);
  const canSubmit = hasVenue && Boolean(selected) && hasDate && hasSlots;
  if (!canSubmit) {
    els.submitBookingButton.disabled = true;
    els.submitBookingButton.textContent = hasVenue ? "确认预约" : "请先选择场馆";
    return;
  }
  els.submitBookingButton.disabled = false;
  els.submitBookingButton.textContent = state.token ? "确认预约" : "登录后预约";
}

function renderProfile() {
  if (!state.profile) {
    els.profileName.textContent = "访客用户";
    els.profileDesc.textContent = "登录后查看预约统计与历史记录";
    els.profileBalance.textContent = "余额 ¥0.00";
    els.profileStats.innerHTML = `
      <div><strong>0</strong><span>待使用</span></div>
      <div><strong>0</strong><span>总预约</span></div>
      <div><strong>0</strong><span>已取消</span></div>
    `;
    return;
  }

  els.profileName.textContent = getDisplayName() || state.profile.username;
  els.profileDesc.textContent = `用户名：${state.profile.username} · ${getRoleLabel(state.profile.role)}`;
  els.profileBalance.textContent = `余额 ¥${(state.profile.balance / 100).toFixed(2)}`;
  els.profileStats.innerHTML = `
    <div><strong>${state.profile.activeReservations}</strong><span>待使用</span></div>
    <div><strong>${state.profile.totalReservations}</strong><span>总预约</span></div>
    <div><strong>${state.profile.cancelledReservations}</strong><span>已取消</span></div>
  `;
}

function populateSettingsForm() {
  els.settingsUsername.value = state.profile?.username || "";
  els.settingsNickname.value = state.profile?.nickname || "";
  els.settingsCurrentPassword.value = "";
  els.settingsNewPassword.value = "";
  els.settingsConfirmPassword.value = "";
  els.settingsProfileMessage.textContent = state.token ? "用户名用于登录，用户昵称会显示在前端页面。" : "请先登录后再设置账号。";
  els.settingsPasswordMessage.textContent = state.token ? "密码修改后，下次登录将使用新密码。" : "请先登录后再设置账号。";
}

async function saveProfileSettings() {
  if (!state.token) {
    setActiveScreen("orders");
    return;
  }
  const nickname = els.settingsNickname.value.trim();
  if (!nickname) {
    els.settingsProfileMessage.textContent = "请输入用户昵称。";
    return;
  }
  if (nickname.length < 2) {
    els.settingsProfileMessage.textContent = "用户昵称至少 2 位。";
    return;
  }

  els.settingsProfileSaveButton.disabled = true;
  els.settingsProfileMessage.textContent = "保存中...";
  try {
    const profile = await api("/user/profile", {
      method: "PUT",
      body: JSON.stringify({ nickname }),
    });
    state.profile = profile;
    populateSettingsForm();
    renderProfile();
    renderAuthChrome();
    showToast("用户昵称已更新。");
  } catch (error) {
    els.settingsProfileMessage.textContent = error.message;
  } finally {
    els.settingsProfileSaveButton.disabled = false;
  }
}

async function savePasswordSettings() {
  if (!state.token) {
    setActiveScreen("orders");
    return;
  }
  const currentPassword = els.settingsCurrentPassword.value;
  const newPassword = els.settingsNewPassword.value;
  const confirmPassword = els.settingsConfirmPassword.value;
  if (!currentPassword.trim()) {
    els.settingsPasswordMessage.textContent = "请输入当前密码。";
    return;
  }
  if (!newPassword.trim()) {
    els.settingsPasswordMessage.textContent = "请输入新密码。";
    return;
  }
  if (newPassword.length < 5) {
    els.settingsPasswordMessage.textContent = "新密码至少 5 位。";
    return;
  }
  if (newPassword !== confirmPassword) {
    els.settingsPasswordMessage.textContent = "两次输入的新密码不一致。";
    return;
  }
  els.settingsPasswordSaveButton.disabled = true;
  els.settingsPasswordMessage.textContent = "修改中...";
  try {
    await api("/user/password", {
      method: "PUT",
      body: JSON.stringify({
        currentPassword,
        newPassword,
      }),
    });
    populateSettingsForm();
    showToast("密码已更新。");
  } catch (error) {
    els.settingsPasswordMessage.textContent = error.message;
  } finally {
    els.settingsPasswordSaveButton.disabled = false;
  }
}

function reservationStatusMeta(status) {
  if (status === 1) {
    return { label: "待使用", klass: "live" };
  }
  if (status === 2) {
    return { label: "已取消", klass: "warn" };
  }
  if (status === 4) {
    return { label: "已完成", klass: "done" };
  }
  return { label: "排队中", klass: "live" };
}

function renderOrders() {
  if (!state.token) {
    els.orderList.innerHTML = "";
    renderOrderFilters();
    return;
  }
  const reservations = getFilteredReservations();
  renderOrderFilters();
  if (!reservations.length) {
    els.orderList.innerHTML = '<div class="empty-state">当前没有预约记录，去“预约”页面创建一条新的预约即可。</div>';
    return;
  }

  els.orderList.innerHTML = reservations.map((item) => {
    const meta = reservationStatusMeta(item.status);
    const canCancel = item.status === 0 || item.status === 1;
    const resourceMeta = getResourceMeta(item.resourceId);
    const resourceTitle = resourceMeta.resourceName || `场地 #${item.resourceId}`;
    const venueTitle = resourceMeta.venueName || `场馆 #${item.venueId}`;
    return `
      <article class="order-card ${canCancel ? "featured" : ""}">
        <div class="order-head">
          <div>
            <h3>${resourceTitle}</h3>
            <p>${formatDate(item.slotDate)} ${formatSlotRange(rangeUnits(item.startUnit, item.endUnit))}</p>
          </div>
          <span class="order-status ${meta.klass}">${meta.label}</span>
        </div>
        <div class="order-tags">
          <span>${venueTitle}</span>
          <span>${item.size} 人</span>
        </div>
        ${canCancel ? `<div class="order-actions"><button class="ghost-btn" data-cancel-id="${item.id}">取消预约</button></div>` : ""}
      </article>
    `;
  }).join("");

  [...els.orderList.querySelectorAll("[data-cancel-id]")].forEach((button) => {
    button.addEventListener("click", async () => {
      await cancelReservation(Number(button.dataset.cancelId));
    });
  });
}

function rangeUnits(start, end) {
  const result = [];
  for (let unit = start; unit <= end; unit += 1) {
    result.push(unit);
  }
  return result;
}

async function submitBooking() {
  const selected = getSelectedResource();
  const sortedUnits = [...state.selectedSlots].sort((a, b) => a - b);
  if (!selected) {
    els.bookingMessage.textContent = "请先选择场馆和场地。";
    return;
  }
  if (!els.bookingDate.value) {
    els.bookingMessage.textContent = "请选择预约日期。";
    return;
  }
  if (!sortedUnits.length) {
    els.bookingMessage.textContent = "请至少选择一个时间段。";
    return;
  }
  if (!isContinuousUnits(sortedUnits)) {
    els.bookingMessage.textContent = "预约时段必须连续。";
    return;
  }
  if (!state.token) {
    els.bookingMessage.textContent = "请先到“我的”页面登录后再提交预约。";
    setActiveScreen("orders");
    return;
  }

  try {
    els.bookingMessage.textContent = "预约提交中...";
    const id = await api("/reservation/apply", {
      method: "POST",
      body: JSON.stringify({
        venueId: selected.venue.id,
        resourceId: selected.resource.id,
        slotDate: `${els.bookingDate.value}T00:00:00`,
        startUnit: sortedUnits[0],
        endUnit: sortedUnits[sortedUnits.length - 1],
        size: Number(els.bookingSize.value || 1),
      }),
    });
    els.bookingMessage.textContent = `预约成功，记录编号 #${id}`;
    resetSlotSelection();
    await loadData();
    setActiveScreen("orders");
  } catch (error) {
    els.bookingMessage.textContent = error.message;
  }
}

async function cancelReservation(id) {
  try {
    await api(`/reservation/${id}/cancel`, { method: "POST" });
    await loadData();
    showToast("预约已取消。");
  } catch (error) {
    showToast(error.message || "取消预约失败。");
  }
}

async function loadAvailability(silent = false) {
  ensureBookingDateIsValid();
  if (!state.selectedVenueId || !state.selectedResourceId || !els.bookingDate.value) {
    state.availability = null;
    return;
  }
  try {
    state.availability = await api(`/reservation/availability?resourceId=${state.selectedResourceId}&slotDate=${els.bookingDate.value}T00:00:00`);
    validateSelectedSlots();
  } catch (error) {
    state.availability = null;
    if (!silent) {
      els.bookingMessage.textContent = error.message;
    }
  }
}

function resetSlotSelection() {
  state.selectedSlots = new Set();
  state.selectionAnchorUnit = null;
}

function syncSelectedResource() {
  const venue = getSelectedVenue();
  if (!venue) {
    state.selectedResourceId = null;
    state.availability = null;
    return;
  }
  const hasCurrentResource = venue.resources.some((item) => item.id === state.selectedResourceId);
  if (!hasCurrentResource) {
    state.selectedResourceId = venue.resources[0]?.id || null;
  }
}

async function selectVenue(venueId) {
  state.selectedVenueId = venueId;
  syncSelectedResource();
  resetSlotSelection();
  await loadAvailability(true);
  renderBookingFlowState();
  renderBookingVenues();
  renderResources();
  renderSlots();
  renderBookingSummary();
  setActiveScreen("booking-detail");
}

function getVisibleAvailabilitySlots() {
  const slots = state.availability?.slots || [];
  const unitMinutes = state.availability?.unitMinutes || getSelectedResource()?.resource?.unitMinutes || 10;
  const startUnit = Math.floor((8 * 60) / unitMinutes);
  const endUnit = Math.floor((22 * 60) / unitMinutes);
  const visible = slots.filter((item) => item.slotUnit >= startUnit && item.slotUnit <= endUnit);
  return visible.length ? visible : slots;
}

function getAvailabilitySlot(unit) {
  return (state.availability?.slots || []).find((item) => item.slotUnit === unit) || null;
}

function isSlotExpired(slot) {
  if (!slot || !isTodayBookingDate(els.bookingDate.value)) {
    return false;
  }
  const slotStartMinutes = toMinutes(slot.startTime);
  return slotStartMinutes < getCurrentMinutesOfDay();
}

function isSlotAvailableForSize(slot) {
  const size = Number(els.bookingSize.value || 1);
  return slot && !isSlotExpired(slot) && slot.status !== 2 && slot.remainingCapacity >= size;
}

function getSlotStateText(slot, disabled) {
  if (!disabled) {
    return `余 ${slot.remainingCapacity}`;
  }
  if (isSlotExpired(slot)) {
    return "已过期";
  }
  return "已满";
}

function ensureBookingDateIsValid() {
  const today = getTodayDateString();
  const maxDate = getMaxBookingDateString();
  els.bookingDate.min = today;
  els.bookingDate.max = maxDate;
  if (isPastBookingDate(els.bookingDate.value)) {
    els.bookingDate.value = today;
    resetSlotSelection();
    showTransientBookingMessage("预约日期不能早于今天，已自动调整。");
  }
  if (isBeyondBookingWindow(els.bookingDate.value)) {
    els.bookingDate.value = maxDate;
    resetSlotSelection();
    showTransientBookingMessage("仅支持预约14天以内的场馆！");
  }
}

function pickSlotUnit(unit) {
  const slot = getAvailabilitySlot(unit);
  if (!isSlotAvailableForSize(slot)) {
    return;
  }
  if (state.selectionAnchorUnit === null || state.selectedSlots.size > 1) {
    state.selectionAnchorUnit = unit;
    state.selectedSlots = new Set([unit]);
    return;
  }
  if (state.selectionAnchorUnit === unit) {
    resetSlotSelection();
    return;
  }
  const units = rangeUnits(Math.min(state.selectionAnchorUnit, unit), Math.max(state.selectionAnchorUnit, unit));
  const canSelectRange = units.every((slotUnit) => isSlotAvailableForSize(getAvailabilitySlot(slotUnit)));
  if (!canSelectRange) {
    els.bookingMessage.textContent = "所选区间包含已满时段，请重新选择。";
    state.selectedSlots = new Set([state.selectionAnchorUnit]);
    return;
  }
  state.selectedSlots = new Set(units);
}

function validateSelectedSlots() {
  const selectedUnits = [...state.selectedSlots];
  if (!selectedUnits.length) {
    return;
  }
  const valid = selectedUnits.every((unit) => isSlotAvailableForSize(getAvailabilitySlot(unit)));
  if (!valid || !isContinuousUnits(selectedUnits.sort((a, b) => a - b))) {
    resetSlotSelection();
    els.bookingMessage.textContent = "原已选时段已变化，请重新选择。";
  }
}

function isContinuousUnits(units) {
  if (!units.length) {
    return false;
  }
  for (let i = 1; i < units.length; i += 1) {
    if (units[i] !== units[i - 1] + 1) {
      return false;
    }
  }
  return true;
}

function bindEvents() {
  tabButtons.forEach((button) => {
    button.addEventListener("click", () => {
      if (button.dataset.screen === "booking") {
        setActiveScreen("booking");
        return;
      }
      setActiveScreen(button.dataset.screen);
    });
  });

  document.querySelectorAll(".filter-chip[data-filter]").forEach((button) => {
    button.addEventListener("click", () => {
      state.ordersFilter = button.dataset.filter;
      renderOrders();
    });
  });
  els.homeAvatar.addEventListener("click", () => setActiveScreen("orders"));
  els.bookingAvatar.addEventListener("click", () => setActiveScreen("orders"));
  els.bookingDetailAvatar.addEventListener("click", () => setActiveScreen("orders"));
  els.heroBookingAction.addEventListener("click", () => {
    if (!state.token) {
      setActiveScreen("orders");
      return;
    }
    setActiveScreen("booking");
  });
  els.bookingSearch.addEventListener("input", () => {
    state.bookingSearchKeyword = els.bookingSearch.value || "";
    renderBookingVenues();
  });
  els.authLoginTab.addEventListener("click", () => setAuthMode("login"));
  els.authRegisterTab.addEventListener("click", () => setAuthMode("register"));
  els.authSubmitButton.addEventListener("click", submitAuthForm);
  els.ordersSettingsButton.addEventListener("click", () => setActiveScreen("settings"));
  els.settingsBackButton.addEventListener("click", () => setActiveScreen("orders"));
  els.settingsProfileSaveButton.addEventListener("click", saveProfileSettings);
  els.settingsPasswordSaveButton.addEventListener("click", savePasswordSettings);
  els.settingsLogoutButton.addEventListener("click", logoutUser);
  els.submitBookingButton.addEventListener("click", submitBooking);
  document.getElementById("refresh-home").addEventListener("click", loadData);
  document.getElementById("back-to-venues").addEventListener("click", () => setActiveScreen("booking"));
  els.bookingDate.addEventListener("change", async () => {
    resetSlotSelection();
    await loadAvailability();
    renderSlots();
    renderBookingSummary();
  });
  els.bookingSize.addEventListener("input", async () => {
    validateSelectedSlots();
    renderSlots();
    renderBookingSummary();
  });
}

function initDefaults() {
  const today = getTodayDateString();
  const maxDate = getMaxBookingDateString();
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  els.bookingDate.min = today;
  els.bookingDate.max = maxDate;
  els.bookingDate.value = formatInputDate(tomorrow);
  renderSlots();
  renderBookingSummary();
  renderAuthChrome();
}

function initFromQuery() {
  setAuthMode("login");
  if (requestedScreen && screens.includes(requestedScreen)) {
    setActiveScreen(requestedScreen);
  }
}

bindEvents();
initDefaults();
initFromQuery();
loadData().then(() => {
  if (requestedScreen && screens.includes(requestedScreen)) {
    setActiveScreen(requestedScreen);
  }
});
