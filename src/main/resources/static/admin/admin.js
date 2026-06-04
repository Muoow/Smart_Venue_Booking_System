const query = new URLSearchParams(window.location.search);
const autoLoginUser = query.get("autologin");

const state = {
  token: localStorage.getItem("courtflow-admin-token") || "",
  activeView: "overview",
  dashboard: null,
  venues: [],
  resources: [],
  reservations: [],
  orders: [],
  payments: [],
  users: [],
  reservationFilterStatus: "",
  reservationFilterDate: "",
  reservationFilterKeyword: "",
  editingVenueId: null,
  editingResourceId: null,
};

const els = {
  loginShell: document.getElementById("admin-login-shell"),
  adminShell: document.getElementById("admin-shell"),
  loginSubtitle: document.getElementById("admin-login-subtitle"),
  adminSubtitle: document.getElementById("admin-subtitle"),
  adminStatus: document.getElementById("admin-status"),
  adminStatusBadge: document.getElementById("admin-status-badge"),
  adminUserBadge: document.getElementById("admin-user-badge"),
  adminKpis: document.getElementById("admin-kpis"),
  venueSummary: document.getElementById("venue-summary"),
  systemStatus: document.getElementById("system-status"),
  reservationTable: document.getElementById("reservation-table"),
  reservationManageTable: document.getElementById("reservation-manage-table"),
  resourceTable: document.getElementById("resource-table"),
  userTable: document.getElementById("user-table"),
  orderTable: document.getElementById("order-table"),
  paymentTable: document.getElementById("payment-table"),
  venueListPanel: document.getElementById("venue-list-panel"),
  loginButton: document.getElementById("admin-login"),
  logoutButton: document.getElementById("admin-logout"),
  username: document.getElementById("admin-username"),
  password: document.getElementById("admin-password"),
  reservationFilterStatus: document.getElementById("reservation-filter-status"),
  reservationFilterDate: document.getElementById("reservation-filter-date"),
  reservationFilterKeyword: document.getElementById("reservation-filter-keyword"),
  venueFormNote: document.getElementById("venue-form-note"),
  resourceFormNote: document.getElementById("resource-form-note"),
  resourceVenueId: document.getElementById("resource-venue-id"),
};

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

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value || "--";
  }
  return date.toLocaleString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatDate(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value || "--";
  }
  return date.toLocaleDateString("zh-CN", {
    month: "2-digit",
    day: "2-digit",
  });
}

function formatSlotRange(startUnit, endUnit, unitMinutes = 10) {
  const toText = (unit) => {
    const totalMinutes = unit * unitMinutes;
    const hour = String(Math.floor(totalMinutes / 60)).padStart(2, "0");
    const minute = String(totalMinutes % 60).padStart(2, "0");
    return `${hour}:${minute}`;
  };
  return `${toText(startUnit)} - ${toText(endUnit + 1)}`;
}

function statusMeta(status) {
  if (status === 1) {
    return { label: "待使用", klass: "live" };
  }
  if (status === 2) {
    return { label: "已取消", klass: "warn" };
  }
  if (status === 4) {
    return { label: "已完成", klass: "done" };
  }
  return { label: "排队中", klass: "queue" };
}

function setView(view) {
  state.activeView = view;
  document.querySelectorAll(".sidebar-nav a").forEach((node) => {
    node.classList.toggle("active", node.dataset.view === view);
  });
  document.querySelectorAll(".admin-view").forEach((node) => {
    node.classList.toggle("active", node.id === `view-${view}`);
  });
  const titles = {
    overview: "运营总览",
    venues: "场馆管理",
    resources: "资源管理",
    reservations: "预约管理",
    orders: "订单管理",
    payments: "支付记录",
    users: "用户信息",
  };
  document.querySelector(".topbar h2").textContent = titles[view] || "管理后台";
}

function setMessage(message, statusText = "已连接") {
  els.adminSubtitle.textContent = message;
  els.adminStatus.textContent = statusText;
  els.adminStatusBadge.textContent = statusText;
  els.loginSubtitle.textContent = message;
}

function setAdminLocked(locked) {
  document.body.dataset.adminAuth = locked ? "locked" : "unlocked";
  els.loginShell.hidden = !locked;
  els.adminShell.hidden = locked;
}

function updateAdminIdentity() {
  els.adminUserBadge.textContent = state.dashboard?.role ? `${state.dashboard.role} 管理员` : "管理员";
}

async function loginAdmin() {
  els.loginButton.disabled = true;
  els.loginButton.textContent = "正在进入...";
  try {
    const token = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({
        username: els.username.value.trim(),
        password: els.password.value.trim(),
      }),
    });
    state.token = token;
    localStorage.setItem("courtflow-admin-token", token);
    await loadData();
  } catch (error) {
    setAdminLocked(true);
    setMessage(error.message, "连接失败");
  } finally {
    els.loginButton.disabled = false;
    els.loginButton.textContent = "进入管理台";
  }
}

function logoutAdmin() {
  state.token = "";
  state.dashboard = null;
  state.venues = [];
  state.resources = [];
  state.reservations = [];
  state.orders = [];
  state.payments = [];
  state.users = [];
  localStorage.removeItem("courtflow-admin-token");
  setAdminLocked(true);
  setMessage("请先使用管理员账号登录，再查看管理内容。", "未登录");
}

function renderKpis() {
  const dashboard = state.dashboard || {};
  const kpis = [
    { label: "开放场馆", value: dashboard.enabledVenueCount ?? "--" },
    { label: "启用资源", value: dashboard.enabledResourceCount ?? "--" },
    { label: "预约总数", value: dashboard.reservationCount ?? "--" },
    { label: "订单总数", value: dashboard.orderCount ?? "--" },
    { label: "待审核支付", value: dashboard.pendingPaymentReviewCount ?? "--" },
    { label: "注册用户", value: dashboard.userCount ?? "--" },
  ];

  els.adminKpis.innerHTML = kpis.map((item) => `
    <article class="metric-card">
      <span>${item.label}</span>
      <strong>${item.value}</strong>
    </article>
  `).join("");
}

function renderVenueSummary() {
  if (!state.venues.length) {
    els.venueSummary.innerHTML = '<div class="empty-state">暂无场馆数据。</div>';
    return;
  }

  els.venueSummary.innerHTML = state.venues.map((venue) => {
    const resources = venue.resources || [];
    const minPrice = resources.length ? Math.min(...resources.map((item) => item.price || 0)) : null;
    const priceText = minPrice !== null ? `¥${minPrice} 起` : "待配置";
    return `
      <article class="summary-card">
        <strong>${venue.name}</strong>
        <p>${venue.type} · ${venue.resourceCount} 个资源 · ${priceText}</p>
      </article>
    `;
  }).join("");
}

function renderSystemStatus() {
  const rows = [
    ["登录状态", state.token ? "已鉴权" : "未登录"],
    ["管理员角色", state.dashboard?.role || "未知"],
    ["场馆数据", `${state.venues.length} 个场馆`],
    ["资源数据", `${state.resources.length} 个资源`],
    ["预约同步", `${state.reservations.length} 条记录`],
    ["订单数据", `${state.orders.length} 条订单`],
    ["支付记录", `${state.payments.length} 条支付`],
    ["待审核支付", `${state.payments.filter((item) => item.reviewable).length} 条`],
  ];

  els.systemStatus.innerHTML = rows.map(([label, value]) => `
    <div class="status-row">
      <strong>${label}</strong>
      <span>${value}</span>
    </div>
  `).join("");
}

function reservationActionButtons(item) {
  const actions = [];
  if (item.status !== 2) {
    actions.push(`<button class="row-btn warn" data-action="cancel-reservation" data-id="${item.id}" type="button">取消</button>`);
  }
  if (item.status === 0 || item.status === 1) {
    actions.push(`<button class="row-btn primary" data-action="finish-reservation" data-id="${item.id}" type="button">完结</button>`);
  }
  return actions.join("");
}

function renderOverviewReservations() {
  const head = `
    <div class="table-head">
      <span>预约编号</span>
      <span>用户</span>
      <span>场馆 / 资源</span>
      <span>日期</span>
      <span>状态</span>
      <span>操作</span>
    </div>
  `;
  const recent = state.dashboard?.recentReservations || [];
  if (!recent.length) {
    els.reservationTable.innerHTML = `${head}<div class="empty-state">暂无预约记录。</div>`;
    return;
  }
  const rows = recent.map((item) => {
    const meta = statusMeta(item.status);
    return `
      <div class="table-row">
        <span>#${item.id}</span>
        <span>${item.username}</span>
        <span>${item.venueName} / ${item.resourceName}</span>
        <span>${formatDate(item.slotDate)}</span>
        <span><i class="status-tag ${meta.klass}">${item.statusLabel || meta.label}</i></span>
        <span class="table-actions">${reservationActionButtons(item)}</span>
      </div>
    `;
  }).join("");
  els.reservationTable.innerHTML = head + rows;
}

function renderVenueManage() {
  if (!state.venues.length) {
    els.venueListPanel.innerHTML = '<div class="empty-state">暂无场馆数据。</div>';
    return;
  }
  els.venueListPanel.innerHTML = state.venues.map((venue) => `
    <article class="summary-card">
      <div class="summary-head">
        <div>
          <strong>${venue.name}</strong>
          <p>${venue.type} · ${venue.resourceCount} 个资源 · ${venue.status === 1 ? "启用" : "禁用"}</p>
        </div>
        <div class="table-actions">
          <button class="row-btn" data-action="edit-venue" data-id="${venue.id}" type="button">编辑</button>
          <button class="row-btn danger" data-action="delete-venue" data-id="${venue.id}" type="button">删除</button>
        </div>
      </div>
    </article>
  `).join("");
}

function renderResourceOptions() {
  if (!state.venues.length) {
    els.resourceVenueId.innerHTML = '<option value="">暂无场馆</option>';
    return;
  }
  els.resourceVenueId.innerHTML = state.venues.map((venue) => `
    <option value="${venue.id}">${venue.name}</option>
  `).join("");
}

function renderResources() {
  const head = `
    <div class="table-head">
      <span>资源编号</span>
      <span>所属场馆</span>
      <span>资源信息</span>
      <span>价格 / 粒度</span>
      <span>状态</span>
      <span>操作</span>
    </div>
  `;
  if (!state.resources.length) {
    els.resourceTable.innerHTML = `${head}<div class="empty-state">暂无资源数据。</div>`;
    return;
  }
  const rows = state.resources.map((item) => `
    <div class="table-row">
      <span>#${item.id}</span>
      <span>${item.venueName}</span>
      <span>${item.name} · ${item.resourceTypeLabel} · 容量 ${item.capacity}</span>
      <span>¥${item.price} / ${item.unitMinutes} 分钟</span>
      <span><i class="status-tag ${item.status === 1 ? "live" : "warn"}">${item.statusLabel}</i></span>
      <span class="table-actions">
        <button class="row-btn" data-action="edit-resource" data-id="${item.id}" type="button">编辑</button>
        <button class="row-btn danger" data-action="delete-resource" data-id="${item.id}" type="button">删除</button>
      </span>
    </div>
  `).join("");
  els.resourceTable.innerHTML = head + rows;
}

function renderReservationManage() {
  const head = `
    <div class="table-head">
      <span>预约编号</span>
      <span>用户</span>
      <span>场馆 / 资源</span>
      <span>日期 / 时段</span>
      <span>状态</span>
      <span>操作</span>
    </div>
  `;
  if (!state.reservations.length) {
    els.reservationManageTable.innerHTML = `${head}<div class="empty-state">暂无预约数据。</div>`;
    return;
  }
  const rows = state.reservations.map((item) => {
    const meta = statusMeta(item.status);
    return `
      <div class="table-row">
        <span>#${item.id}</span>
        <span>${item.username}</span>
        <span>${item.venueName} / ${item.resourceName}</span>
        <span>${formatDate(item.slotDate)} · ${formatSlotRange(item.startUnit, item.endUnit)}</span>
        <span><i class="status-tag ${meta.klass}">${item.statusLabel || meta.label}</i></span>
        <span class="table-actions">${reservationActionButtons(item)}</span>
      </div>
    `;
  }).join("");
  els.reservationManageTable.innerHTML = head + rows;
}

function renderUsers() {
  const head = `
    <div class="table-head">
      <span>用户编号</span>
      <span>账号信息</span>
      <span>角色</span>
      <span>状态 / 余额</span>
      <span>预约 / 订单</span>
      <span>操作</span>
    </div>
  `;
  if (!state.users.length) {
    els.userTable.innerHTML = `${head}<div class="empty-state">暂无用户数据。</div>`;
    return;
  }
  const rows = state.users.map((item) => `
    <div class="table-row">
      <span>#${item.id}</span>
      <span>${item.username}</span>
      <span>
        <select class="row-select" data-role-select="${item.id}">
          <option value="USER" ${item.role === "USER" ? "selected" : ""}>USER</option>
          <option value="ADMIN" ${item.role === "ADMIN" ? "selected" : ""}>ADMIN</option>
        </select>
      </span>
      <span class="user-inline-edit">
        <select class="row-select" data-status-select="${item.id}">
          <option value="1" ${item.status === 1 ? "selected" : ""}>启用</option>
          <option value="0" ${item.status === 0 ? "selected" : ""}>禁用</option>
        </select>
        <input class="row-input" data-balance-input="${item.id}" type="number" min="0" value="${item.balance || 0}">
      </span>
      <span>${item.reservationCount} / ${item.orderCount || 0}</span>
      <span class="table-actions">
        <button class="row-btn primary" data-action="save-user" data-id="${item.id}" type="button">保存</button>
      </span>
    </div>
  `).join("");
  els.userTable.innerHTML = head + rows;
}

function renderOrders() {
  const head = `
    <div class="table-head">
      <span>订单号</span>
      <span>用户</span>
      <span>金额</span>
      <span>预约信息</span>
      <span>状态</span>
      <span>操作</span>
    </div>
  `;
  if (!state.orders.length) {
    els.orderTable.innerHTML = `${head}<div class="empty-state">暂无订单数据。</div>`;
    return;
  }
  const rows = state.orders.map((item) => `
    <div class="table-row">
      <span>${item.orderNo}</span>
      <span>${item.username}</span>
      <span>¥${((item.totalAmount || 0) / 100).toFixed(2)}</span>
      <span>${item.reservationId ? `预约 #${item.reservationId}` : "--"}</span>
      <span><i class="status-tag ${item.status === 1 ? "done" : item.status === 3 ? "warn" : "queue"}">${item.statusLabel}</i></span>
      <span class="table-actions">
        ${item.status === 0 ? `<button class="row-btn warn" data-action="close-order" data-id="${item.id}" type="button">关闭</button>` : ""}
        ${item.status === 1 ? `<button class="row-btn danger" data-action="refund-order" data-id="${item.id}" type="button">退款</button>` : ""}
      </span>
    </div>
  `).join("");
  els.orderTable.innerHTML = head + rows;
}

function renderPayments() {
  const head = `
    <div class="table-head">
      <span>支付编号</span>
      <span>交易类型</span>
      <span>订单号</span>
      <span>支付渠道</span>
      <span>支付金额</span>
      <span>支付状态</span>
      <span>处理说明</span>
      <span>完成时间</span>
      <span>操作</span>
    </div>
  `;
  if (!state.payments.length) {
    els.paymentTable.innerHTML = `${head}<div class="empty-state">暂无支付记录。</div>`;
    return;
  }
  const rows = state.payments.map((item) => `
    <div class="table-row">
      <span>#${item.id}</span>
      <span>${item.bizTypeLabel || "--"}</span>
      <span>${item.orderNo || `订单 #${item.orderId}`}</span>
      <span>${item.payChannelLabel || `渠道 ${item.payChannel}`}</span>
      <span>¥${((item.payAmount || 0) / 100).toFixed(2)}</span>
      <span><i class="status-tag ${item.payStatus === 1 ? "done" : item.payStatus === 2 ? "warn" : "queue"}">${item.payStatusLabel || "处理中"}</i></span>
      <span>${item.statusNote || "--"}</span>
      <span>${formatDateTime(item.paidAt || item.processedAt || item.createdAt)}</span>
      <span class="table-actions">
        ${item.reviewable ? `<button class="row-btn primary" data-action="approve-payment" data-id="${item.id}" type="button">通过</button>` : ""}
        ${item.reviewable ? `<button class="row-btn danger" data-action="reject-payment" data-id="${item.id}" type="button">驳回</button>` : ""}
      </span>
    </div>
  `).join("");
  els.paymentTable.innerHTML = head + rows;
}

function fillVenueForm(venue) {
  document.getElementById("venue-id").value = venue?.id || "";
  document.getElementById("venue-name").value = venue?.name || "";
  document.getElementById("venue-type").value = venue?.type || "";
  document.getElementById("venue-status").value = String(venue?.status ?? 1);
  state.editingVenueId = venue?.id || null;
  els.venueFormNote.textContent = venue ? `编辑场馆 #${venue.id}` : "新建场馆";
}

function fillResourceForm(resource) {
  document.getElementById("resource-id").value = resource?.id || "";
  document.getElementById("resource-venue-id").value = String(resource?.venueId || state.venues[0]?.id || "");
  document.getElementById("resource-name").value = resource?.name || "";
  document.getElementById("resource-type").value = String(resource?.resourceType ?? 1);
  document.getElementById("resource-capacity").value = resource?.capacity ?? 4;
  document.getElementById("resource-price").value = resource?.price ?? 50;
  document.getElementById("resource-unit-minutes").value = resource?.unitMinutes ?? 10;
  document.getElementById("resource-status").value = String(resource?.status ?? 1);
  state.editingResourceId = resource?.id || null;
  els.resourceFormNote.textContent = resource ? `编辑资源 #${resource.id}` : "新建资源";
}

async function loadData() {
  if (!state.token) {
    setAdminLocked(true);
    setMessage("请先使用管理员账号登录。", "未登录");
    return;
  }
  try {
    const reservationParams = new URLSearchParams({
      pageNumber: "1",
      pageSize: "50",
    });
    if (state.reservationFilterStatus) {
      reservationParams.set("status", state.reservationFilterStatus);
    }
    if (state.reservationFilterDate) {
      reservationParams.set("slotDate", state.reservationFilterDate);
    }
    if (state.reservationFilterKeyword) {
      reservationParams.set("keyword", state.reservationFilterKeyword);
    }
    const [dashboard, venues, resources, reservations, users, orders, payments] = await Promise.all([
      api("/admin/dashboard"),
      api("/admin/venues"),
      api("/admin/resources"),
      api(`/admin/reservations?${reservationParams.toString()}`),
      api("/admin/users"),
      api("/admin/orders"),
      api("/admin/payments"),
    ]);
    state.dashboard = dashboard;
    state.venues = venues;
    state.resources = resources;
    state.reservations = reservations.records || [];
    state.users = users;
    state.orders = orders;
    state.payments = payments;

    setAdminLocked(false);
    setMessage(`当前接入 ${venues.length} 个场馆、${resources.length} 个资源、${reservations.total || state.reservations.length} 条预约`, "已连接");
    updateAdminIdentity();
    renderKpis();
    renderVenueSummary();
    renderSystemStatus();
    renderOverviewReservations();
    renderVenueManage();
    renderResourceOptions();
    renderResources();
    renderReservationManage();
    renderUsers();
    renderOrders();
    renderPayments();
    if (!state.editingVenueId) {
      fillVenueForm(null);
    }
    if (!state.editingResourceId) {
      fillResourceForm(null);
    }
  } catch (error) {
    if (error.status === 401 || error.status === 403) {
      state.token = "";
      localStorage.removeItem("courtflow-admin-token");
      setAdminLocked(true);
    }
    setMessage(error.message, "加载失败");
    els.venueSummary.innerHTML = `<div class="empty-state">${error.message}</div>`;
    els.systemStatus.innerHTML = `<div class="empty-state">${error.message}</div>`;
    els.reservationTable.innerHTML = `<div class="empty-state">${error.message}</div>`;
    els.venueListPanel.innerHTML = `<div class="empty-state">${error.message}</div>`;
    els.resourceTable.innerHTML = `<div class="empty-state">${error.message}</div>`;
    els.reservationManageTable.innerHTML = `<div class="empty-state">${error.message}</div>`;
    els.userTable.innerHTML = `<div class="empty-state">${error.message}</div>`;
    els.orderTable.innerHTML = `<div class="empty-state">${error.message}</div>`;
    els.paymentTable.innerHTML = `<div class="empty-state">${error.message}</div>`;
  }
}

function syncReservationFilters() {
  state.reservationFilterStatus = els.reservationFilterStatus.value;
  state.reservationFilterDate = els.reservationFilterDate.value;
  state.reservationFilterKeyword = els.reservationFilterKeyword.value.trim();
}

async function saveVenue(event) {
  event.preventDefault();
  const payload = {
    name: document.getElementById("venue-name").value.trim(),
    type: document.getElementById("venue-type").value.trim(),
    status: Number(document.getElementById("venue-status").value),
  };
  if (!payload.name || !payload.type) {
    setMessage("请先填写完整的场馆信息。", "待完善");
    return;
  }
  const path = state.editingVenueId ? `/admin/venues/${state.editingVenueId}` : "/admin/venues";
  const method = state.editingVenueId ? "PUT" : "POST";
  await api(path, {
    method,
    body: JSON.stringify(payload),
  });
  fillVenueForm(null);
  await loadData();
}

async function saveResource(event) {
  event.preventDefault();
  const payload = {
    venueId: Number(document.getElementById("resource-venue-id").value),
    name: document.getElementById("resource-name").value.trim(),
    resourceType: Number(document.getElementById("resource-type").value),
    capacity: Number(document.getElementById("resource-capacity").value),
    price: Number(document.getElementById("resource-price").value),
    unitMinutes: Number(document.getElementById("resource-unit-minutes").value),
    status: Number(document.getElementById("resource-status").value),
  };
  if (!payload.venueId || !payload.name) {
    setMessage("请先填写完整的资源信息。", "待完善");
    return;
  }
  const path = state.editingResourceId ? `/admin/resources/${state.editingResourceId}` : "/admin/resources";
  const method = state.editingResourceId ? "PUT" : "POST";
  await api(path, {
    method,
    body: JSON.stringify(payload),
  });
  fillResourceForm(null);
  await loadData();
}

async function handleTableAction(event) {
  const button = event.target.closest("[data-action]");
  if (!button) {
    return;
  }
  const { action, id } = button.dataset;
  const numericId = Number(id);

  if (action === "edit-venue") {
    fillVenueForm(state.venues.find((item) => item.id === numericId));
    setView("venues");
    return;
  }

  if (action === "delete-venue") {
    if (!window.confirm("确认删除该场馆吗？")) {
      return;
    }
    await api(`/admin/venues/${numericId}`, { method: "DELETE" });
    await loadData();
    return;
  }

  if (action === "edit-resource") {
    fillResourceForm(state.resources.find((item) => item.id === numericId));
    setView("resources");
    return;
  }

  if (action === "delete-resource") {
    if (!window.confirm("确认删除该资源吗？")) {
      return;
    }
    await api(`/admin/resources/${numericId}`, { method: "DELETE" });
    await loadData();
    return;
  }

  if (action === "cancel-reservation") {
    await api(`/admin/reservations/${numericId}/cancel`, { method: "POST" });
    await loadData();
    return;
  }

  if (action === "finish-reservation") {
    await api(`/admin/reservations/${numericId}/finish`, { method: "POST" });
    await loadData();
    return;
  }

  if (action === "save-user") {
    const role = document.querySelector(`[data-role-select="${numericId}"]`)?.value;
    const status = Number(document.querySelector(`[data-status-select="${numericId}"]`)?.value);
    const balance = Number(document.querySelector(`[data-balance-input="${numericId}"]`)?.value);
    await api(`/admin/users/${numericId}`, {
      method: "PUT",
      body: JSON.stringify({ role, status, balance }),
    });
    await loadData();
    return;
  }

  if (action === "close-order") {
    await api(`/admin/orders/${numericId}/close`, { method: "POST" });
    await loadData();
    return;
  }

  if (action === "refund-order") {
    await api(`/admin/orders/${numericId}/refund`, { method: "POST" });
    await loadData();
    return;
  }

  if (action === "approve-payment") {
    const note = window.prompt("可选填写审核说明：", "审核通过");
    await api(`/admin/payments/${numericId}/approve`, {
      method: "POST",
      body: JSON.stringify({ note: note || "" }),
    });
    await loadData();
    return;
  }

  if (action === "reject-payment") {
    const note = window.prompt("请输入驳回原因：", "审核驳回");
    await api(`/admin/payments/${numericId}/reject`, {
      method: "POST",
      body: JSON.stringify({ note: note || "审核驳回" }),
    });
    await loadData();
  }
}

function bindEvents() {
  els.loginButton.addEventListener("click", loginAdmin);
  els.logoutButton.addEventListener("click", logoutAdmin);
  document.getElementById("refresh-admin").addEventListener("click", loadData);
  document.getElementById("venue-form").addEventListener("submit", saveVenue);
  document.getElementById("resource-form").addEventListener("submit", saveResource);
  document.getElementById("reset-venue").addEventListener("click", () => fillVenueForm(null));
  document.getElementById("reset-resource").addEventListener("click", () => fillResourceForm(null));
  document.getElementById("apply-reservation-filter").addEventListener("click", async () => {
    syncReservationFilters();
    await loadData();
  });
  els.reservationFilterKeyword.addEventListener("keydown", async (event) => {
    if (event.key !== "Enter") {
      return;
    }
    event.preventDefault();
    syncReservationFilters();
    await loadData();
  });

  document.querySelector(".sidebar-nav").addEventListener("click", (event) => {
    const link = event.target.closest("[data-view]");
    if (!link) {
      return;
    }
    setView(link.dataset.view);
  });

  document.querySelector(".admin-main").addEventListener("click", async (event) => {
    try {
      await handleTableAction(event);
    } catch (error) {
      setMessage(error.message, "操作失败");
    }
  });
}

bindEvents();
setView("overview");

if (autoLoginUser && !state.token) {
  setAdminLocked(true);
  els.username.value = autoLoginUser;
  els.password.value = "demo";
  loginAdmin();
} else if (state.token) {
  loadData();
} else {
  setAdminLocked(true);
  setMessage("请输入管理员账号并登录后继续查看运营数据。", "未登录");
}
