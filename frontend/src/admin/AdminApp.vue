<script setup>
import { computed, onMounted, provide, reactive } from 'vue'
import VChart, { THEME_KEY } from 'vue-echarts'
import {
  Building2,
  CalendarDays,
  Check,
  CreditCard,
  Filter,
  LayoutDashboard,
  LogIn,
  LogOut,
  Package,
  Pencil,
  ReceiptText,
  RefreshCw,
  RotateCcw,
  Save,
  Trash2,
  Users,
  X,
} from 'lucide-vue-next'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent,
} from 'echarts/components'
import { apiRequest } from '../shared/api'
import { ADMIN_TOKEN_STORAGE_KEY } from '../shared/constants'
import { formatDate, formatDateTime, formatMinutes } from '../shared/formatters'

use([
  CanvasRenderer,
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  TitleComponent,
])

provide(THEME_KEY, {
  color: ['#0891b2', '#22d3ee', '#22c55e', '#6366f1', '#f59e0b', '#ef4444'],
  textStyle: {
    fontFamily: '"Fira Sans", "Segoe UI", "PingFang SC", sans-serif',
  },
})

const query = new URLSearchParams(window.location.search)
const autoLoginUser = query.get('autologin')
const venueAdminViewKeys = ['reservations', 'resources']

const viewTitles = {
  overview: '运营总览',
  venues: '场馆管理',
  resources: '资源管理',
  reservations: '预约管理',
  orders: '订单管理',
  payments: '支付记录',
  users: '用户信息',
}

const menuItems = [
  { key: 'overview', label: viewTitles.overview, icon: LayoutDashboard },
  { key: 'venues', label: viewTitles.venues, icon: Building2 },
  { key: 'resources', label: viewTitles.resources, icon: Package },
  { key: 'reservations', label: viewTitles.reservations, icon: CalendarDays },
  { key: 'orders', label: viewTitles.orders, icon: ReceiptText },
  { key: 'payments', label: viewTitles.payments, icon: CreditCard },
  { key: 'users', label: viewTitles.users, icon: Users },
]

const state = reactive({
  token: localStorage.getItem(ADMIN_TOKEN_STORAGE_KEY) || '',
  activeView: 'overview',
  adminProfile: null,
  dashboard: null,
  venues: [],
  resources: [],
  reservations: [],
  orders: [],
  payments: [],
  users: [],
  reservationFilterStatus: '',
  reservationFilterDate: '',
  reservationFilterKeyword: '',
  editingVenueId: null,
  editingResourceId: null,
  isLocked: true,
  message: '请先使用管理员账号登录。',
  statusText: '未登录',
  loginForm: {
    username: 'admin',
    password: '12345',
  },
  venueForm: {
    id: '',
    name: '',
    type: '',
    status: 1,
  },
  resourceForm: {
    id: '',
    venueId: '',
    name: '',
    resourceType: 1,
    capacity: 4,
    price: 50,
    unitMinutes: 10,
    status: 1,
  },
  userEdits: {},
})

const currentRole = computed(() => state.adminProfile?.role || '')
const isSuperAdmin = computed(() => currentRole.value === 'ADMIN')
const isVenueAdmin = computed(() => currentRole.value === 'VENUE_ADMIN')
const visibleMenuItems = computed(() => (
  isVenueAdmin.value
    ? menuItems.filter((item) => venueAdminViewKeys.includes(item.key))
    : menuItems
))
const allowedViewKeys = computed(() => visibleMenuItems.value.map((item) => item.key))
const currentViewTitle = computed(() => viewTitles[state.activeView] || viewTitles[allowedViewKeys.value[0]] || '管理后台')
const adminRoleLabel = computed(() => {
  if (currentRole.value === 'ADMIN') {
    return '超级管理员'
  }
  if (currentRole.value === 'VENUE_ADMIN') {
    return '场地管理员'
  }
  return '管理员'
})
const canEditVenueDetails = computed(() => isSuperAdmin.value)

const kpis = computed(() => {
  const dashboard = state.dashboard || {}
  return [
    { label: '开放场馆', value: dashboard.enabledVenueCount ?? '--' },
    { label: '启用资源', value: dashboard.enabledResourceCount ?? '--' },
    { label: '预约总数', value: dashboard.reservationCount ?? '--' },
    { label: '订单总数', value: dashboard.orderCount ?? '--' },
    { label: '待审核支付', value: dashboard.pendingPaymentReviewCount ?? '--' },
    { label: '注册用户', value: dashboard.userCount ?? '--' },
  ]
})

const venueSummaryList = computed(() => state.venues.map((venue) => {
  const resources = venue.resources || []
  const minPrice = resources.length ? Math.min(...resources.map((item) => item.price || 0)) : null
  return {
    ...venue,
    priceText: minPrice !== null ? `¥${minPrice} 起` : '待配置',
  }
}))

const statusColorMap = {
  排队中: '#38bdf8',
  待使用: '#22c55e',
  使用中: '#14b8a6',
  已取消: '#f59e0b',
  已完成: '#6366f1',
  未支付: '#f59e0b',
  已支付: '#22c55e',
  已关闭: '#94a3b8',
  已退款: '#ef4444',
  处理中: '#38bdf8',
  失败: '#ef4444',
}

function buildCountMap(list, getLabel) {
  return list.reduce((accumulator, item) => {
    const label = getLabel(item)
    accumulator[label] = (accumulator[label] || 0) + 1
    return accumulator
  }, {})
}

const reservationsTrendOption = computed(() => {
  const totalMap = {}
  const finishedMap = {}
  const dateKeys = state.reservations
    .map((item) => String(item.slotDate || '').slice(0, 10))
    .filter(Boolean)
    .sort()

  const endDate = dateKeys.length ? new Date(`${dateKeys[dateKeys.length - 1]}T00:00:00`) : new Date()
  endDate.setHours(0, 0, 0, 0)

  state.reservations.forEach((item) => {
    const dateKey = String(item.slotDate || '').slice(0, 10)
    if (!dateKey) {
      return
    }
    totalMap[dateKey] = (totalMap[dateKey] || 0) + 1
    if (item.status === 4) {
      finishedMap[dateKey] = (finishedMap[dateKey] || 0) + 1
    }
  })

  const labels = Array.from({ length: 7 }, (_, index) => {
    const current = new Date(endDate)
    current.setDate(endDate.getDate() - (6 - index))
    return current.toISOString().slice(0, 10)
  })

  return {
    color: ['#0891b2', '#6366f1'],
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b' },
    },
    grid: { left: 12, right: 12, top: 38, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: labels,
      axisLine: { lineStyle: { color: '#d8e5f1' } },
      axisLabel: {
        color: '#64748b',
        formatter: (value) => value.slice(5).replace('-', '/'),
      },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#e7eff7' } },
      axisLabel: { color: '#64748b' },
    },
    series: [
      {
        name: '预约量',
        type: 'line',
        smooth: true,
        symbolSize: 7,
        areaStyle: { color: 'rgba(8, 145, 178, 0.16)' },
        lineStyle: { width: 3 },
        itemStyle: { color: '#0891b2' },
        data: labels.map((label) => totalMap[label] || 0),
      },
      {
        name: '完成量',
        type: 'line',
        smooth: true,
        symbolSize: 6,
        lineStyle: { width: 2.5 },
        itemStyle: { color: '#6366f1' },
        data: labels.map((label) => finishedMap[label] || 0),
      },
    ],
  }
})

const reservationStatusOption = computed(() => {
  const countMap = buildCountMap(
    state.reservations,
    (item) => item.statusLabel || statusMeta(item.status).label,
  )
  const data = Object.entries(countMap).map(([name, value]) => ({
    name,
    value,
    itemStyle: { color: statusColorMap[name] || '#0891b2' },
  }))

  return {
    tooltip: { trigger: 'item' },
    legend: {
      bottom: 0,
      itemWidth: 10,
      itemHeight: 10,
      textStyle: { color: '#64748b' },
    },
    series: [{
      name: '预约状态',
      type: 'pie',
      radius: ['56%', '76%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      label: { formatter: '{b}\n{c}', color: '#334155' },
      labelLine: { length: 10, length2: 10 },
      data,
    }],
  }
})

const venueResourceOption = computed(() => {
  const data = [...state.venues]
    .map((venue) => ({
      name: venue.name,
      value: venue.resourceCount || venue.resources?.length || 0,
    }))
    .sort((left, right) => right.value - left.value)
    .slice(0, 6)
    .reverse()

  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 12, right: 18, top: 12, bottom: 8, containLabel: true },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#e7eff7' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'category',
      data: data.map((item) => item.name),
      axisLabel: { color: '#475569' },
      axisTick: { show: false },
      axisLine: { show: false },
    },
    series: [{
      type: 'bar',
      data: data.map((item) => item.value),
      barWidth: 16,
      itemStyle: {
        borderRadius: [0, 10, 10, 0],
        color: (params) => ['#38bdf8', '#22d3ee', '#0891b2', '#0ea5b7', '#14b8a6', '#22c55e'][params.dataIndex % 6],
      },
    }],
  }
})

const overviewHeroStats = computed(() => {
  const enabledResources = state.resources.filter((item) => item.status === 1)
  const enabledResourceIds = new Set(enabledResources.map((item) => item.id))
  const reservedResourceIds = new Set(
    state.reservations
      .filter((item) => enabledResourceIds.has(item.resourceId))
      .map((item) => item.resourceId),
  )
  const utilizationRate = enabledResourceIds.size
    ? `${Math.round((reservedResourceIds.size / enabledResourceIds.size) * 100)}%`
    : '--'

  const countMap = buildCountMap(
    state.payments,
    (item) => item.payStatusLabel || '处理中',
  )
  const summary = Object.entries(countMap).map(([label, value]) => ({
    label,
    value,
    color: statusColorMap[label] || '#0891b2',
  }))

  const successIndex = summary.findIndex((item) => ['成功', '已支付'].includes(item.label))
  const utilizationStat = {
    label: '场地综合利用率',
    value: utilizationRate,
    color: '#0ea5b7',
  }

  if (successIndex >= 0) {
    summary.splice(successIndex, 1, utilizationStat)
    return summary
  }

  return [utilizationStat, ...summary]
})

const systemStatusRows = computed(() => ([
  ['登录状态', state.token ? '已鉴权' : '未登录'],
  ['管理员角色', state.dashboard?.role || '未知'],
  ['场馆数据', `${state.venues.length} 个场馆`],
  ['资源数据', `${state.resources.length} 个资源`],
  ['预约同步', `${state.reservations.length} 条记录`],
  ['订单数据', `${state.orders.length} 条订单`],
  ['支付记录', `${state.payments.length} 条支付`],
  ['待审核支付', `${state.payments.filter((item) => item.reviewable).length} 条`],
]))

const recentReservations = computed(() => state.dashboard?.recentReservations || [])
const venueOptions = computed(() => state.venues)

function syncBodyAuth() {
  document.body.dataset.adminAuth = state.isLocked ? 'locked' : 'unlocked'
}

function setAdminLocked(locked) {
  state.isLocked = locked
  syncBodyAuth()
}

function setMessage(message, statusText = '已连接') {
  state.message = message
  state.statusText = statusText
}

function setView(view) {
  if (!allowedViewKeys.value.includes(view)) {
    return
  }
  state.activeView = view
}

function ensureActiveViewAllowed() {
  if (!allowedViewKeys.value.length) {
    state.activeView = 'overview'
    return
  }
  if (!allowedViewKeys.value.includes(state.activeView)) {
    state.activeView = allowedViewKeys.value[0]
  }
}

function statusMeta(status) {
  if (status === 1) {
    return { label: '待使用', klass: 'live' }
  }
  if (status === 5) {
    return { label: '使用中', klass: 'live' }
  }
  if (status === 2) {
    return { label: '已取消', klass: 'warn' }
  }
  if (status === 4) {
    return { label: '已完成', klass: 'done' }
  }
  return { label: '排队中', klass: 'queue' }
}

function formatReservationSlot(item) {
  const unitMinutes = resolveReservationUnitMinutes(item)
  const start = formatMinutes(item.startUnit * unitMinutes)
  const end = formatMinutes((item.endUnit + 1) * unitMinutes)
  return `${start} - ${end}`
}

function resolveReservationUnitMinutes(item) {
  const resource = state.resources.find((resourceItem) => resourceItem.id === item.resourceId)
  return resource?.unitMinutes || 10
}

function paymentStatusClass(payment) {
  if (payment.payStatus === 1) {
    return 'done'
  }
  if (payment.payStatus === 2) {
    return 'warn'
  }
  return 'queue'
}

function orderStatusClass(order) {
  if (order.status === 1) {
    return 'done'
  }
  if (order.status === 3) {
    return 'warn'
  }
  return 'queue'
}

function reservationActionButtons(item) {
  return {
    canCancel: item.status === 0 || item.status === 1,
    canCheckIn: item.status === 1,
    canFinish: item.status === 5,
  }
}

function resetVenueForm() {
  if (isVenueAdmin.value && state.venues.length) {
    fillVenueForm(state.venues[0])
    return
  }
  state.venueForm = {
    id: '',
    name: '',
    type: '',
    status: 1,
  }
  state.editingVenueId = null
}

function resetResourceForm() {
  state.resourceForm = {
    id: '',
    venueId: String(state.venues[0]?.id || ''),
    name: '',
    resourceType: 1,
    capacity: 4,
    price: 50,
    unitMinutes: 10,
    status: 1,
  }
  state.editingResourceId = null
}

function fillVenueForm(venue) {
  if (!venue) {
    resetVenueForm()
    return
  }
  state.venueForm = {
    id: String(venue.id),
    name: venue.name || '',
    type: venue.type || '',
    status: Number(venue.status ?? 1),
  }
  state.editingVenueId = venue.id
}

function fillResourceForm(resource) {
  if (!resource) {
    resetResourceForm()
    return
  }
  state.resourceForm = {
    id: String(resource.id),
    venueId: String(resource.venueId || state.venues[0]?.id || ''),
    name: resource.name || '',
    resourceType: Number(resource.resourceType ?? 1),
    capacity: Number(resource.capacity ?? 4),
    price: Number(resource.price ?? 50),
    unitMinutes: Number(resource.unitMinutes ?? 10),
    status: Number(resource.status ?? 1),
  }
  state.editingResourceId = resource.id
}

function syncUserEdits() {
  const nextEdits = {}
  for (const user of state.users) {
    nextEdits[user.id] = {
      role: user.role,
      status: Number(user.status),
      balance: Number(user.balance || 0),
    }
  }
  state.userEdits = nextEdits
}

async function adminRequest(path, options = {}) {
  return apiRequest(path, options, state.token)
}

async function loginAdmin() {
  try {
    const token = await apiRequest('/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        username: state.loginForm.username.trim(),
        password: state.loginForm.password.trim(),
      }),
    })
    state.token = token
    localStorage.setItem(ADMIN_TOKEN_STORAGE_KEY, token)
    await loadData()
  } catch (error) {
    setAdminLocked(true)
    setMessage(error.message, '连接失败')
  }
}

function logoutAdmin() {
  state.token = ''
  state.adminProfile = null
  state.dashboard = null
  state.venues = []
  state.resources = []
  state.reservations = []
  state.orders = []
  state.payments = []
  state.users = []
  localStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY)
  state.activeView = 'overview'
  setAdminLocked(true)
  setMessage('请先使用管理员账号登录，再查看管理内容。', '未登录')
}

async function loadData() {
  if (!state.token) {
    setAdminLocked(true)
    setMessage('请先使用管理员账号登录。', '未登录')
    return
  }

  try {
    const profile = await adminRequest('/admin/profile')
    state.adminProfile = profile
    ensureActiveViewAllowed()

    const reservationParams = new URLSearchParams({
      pageNumber: '1',
      pageSize: '50',
    })
    if (state.reservationFilterStatus) {
      reservationParams.set('status', state.reservationFilterStatus)
    }
    if (state.reservationFilterDate) {
      reservationParams.set('slotDate', state.reservationFilterDate)
    }
    if (state.reservationFilterKeyword) {
      reservationParams.set('keyword', state.reservationFilterKeyword)
    }

    const [venues, resources, reservations, dashboard, users, orders, payments] = await Promise.all([
      adminRequest('/admin/venues'),
      adminRequest('/admin/resources'),
      adminRequest(`/admin/reservations?${reservationParams.toString()}`),
      isSuperAdmin.value ? adminRequest('/admin/dashboard') : Promise.resolve(null),
      isSuperAdmin.value ? adminRequest('/admin/users') : Promise.resolve([]),
      isSuperAdmin.value ? adminRequest('/admin/orders') : Promise.resolve([]),
      isSuperAdmin.value ? adminRequest('/admin/payments') : Promise.resolve([]),
    ])

    state.venues = venues
    state.resources = resources
    state.reservations = reservations.records || []
    state.dashboard = dashboard
    state.users = users
    state.orders = orders
    state.payments = payments

    syncUserEdits()
    setAdminLocked(false)
    if (isVenueAdmin.value) {
      const managedVenueNames = state.adminProfile?.managedVenueNames || []
      const managedVenueText = managedVenueNames.length ? managedVenueNames.join('、') : `${venues.length} 个场馆`
      setMessage(`当前管理 ${managedVenueText}，已同步 ${reservations.total || state.reservations.length} 条预约记录`, '已连接')
    } else {
      setMessage(`当前接入 ${venues.length} 个场馆、${resources.length} 个资源、${reservations.total || state.reservations.length} 条预约`, '已连接')
    }

    if (!state.editingVenueId) {
      resetVenueForm()
    }
    if (!state.editingResourceId) {
      resetResourceForm()
    }
  } catch (error) {
    if ([401, 403].includes(error.status) || [401, 403].includes(error.code)) {
      state.token = ''
      state.adminProfile = null
      localStorage.removeItem(ADMIN_TOKEN_STORAGE_KEY)
      setAdminLocked(true)
      setMessage('请使用管理员账号重新登录。', '未登录')
      return
    }
    setMessage(error.message, '加载失败')
  }
}

async function applyReservationFilter() {
  await loadData()
}

async function saveVenue() {
  const payload = {
    name: state.venueForm.name.trim(),
    type: state.venueForm.type.trim(),
    status: Number(state.venueForm.status),
  }
  if (!payload.name || !payload.type) {
    setMessage('请先填写完整的场馆信息。', '待完善')
    return
  }
  const path = state.editingVenueId ? `/admin/venues/${state.editingVenueId}` : '/admin/venues'
  const method = state.editingVenueId ? 'PUT' : 'POST'
  await adminRequest(path, {
    method,
    body: JSON.stringify(payload),
  })
  resetVenueForm()
  await loadData()
}

async function updateVenueStatus(id, status) {
  await adminRequest(`/admin/venues/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
  await loadData()
}

async function saveResource() {
  const payload = {
    venueId: Number(state.resourceForm.venueId),
    name: state.resourceForm.name.trim(),
    resourceType: Number(state.resourceForm.resourceType),
    capacity: Number(state.resourceForm.capacity),
    price: Number(state.resourceForm.price),
    unitMinutes: Number(state.resourceForm.unitMinutes),
    status: Number(state.resourceForm.status),
  }
  if (!payload.venueId || !payload.name) {
    setMessage('请先填写完整的资源信息。', '待完善')
    return
  }
  const path = state.editingResourceId ? `/admin/resources/${state.editingResourceId}` : '/admin/resources'
  const method = state.editingResourceId ? 'PUT' : 'POST'
  await adminRequest(path, {
    method,
    body: JSON.stringify(payload),
  })
  resetResourceForm()
  await loadData()
}

async function deleteVenue(id) {
  if (!window.confirm('确认删除该场馆吗？')) {
    return
  }
  await adminRequest(`/admin/venues/${id}`, { method: 'DELETE' })
  await loadData()
}

async function deleteResource(id) {
  if (!window.confirm('确认删除该资源吗？')) {
    return
  }
  await adminRequest(`/admin/resources/${id}`, { method: 'DELETE' })
  await loadData()
}

async function cancelReservation(id) {
  await adminRequest(`/admin/reservations/${id}/cancel`, { method: 'POST' })
  await loadData()
}

async function checkInReservation(id) {
  await adminRequest(`/admin/reservations/${id}/check-in`, { method: 'POST' })
  await loadData()
}

async function finishReservation(id) {
  await adminRequest(`/admin/reservations/${id}/finish`, { method: 'POST' })
  await loadData()
}

async function saveUser(id) {
  const edit = state.userEdits[id]
  await adminRequest(`/admin/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify({
      role: edit.role,
      status: Number(edit.status),
      balance: Number(edit.balance),
    }),
  })
  await loadData()
}

async function closeOrder(id) {
  await adminRequest(`/admin/orders/${id}/close`, { method: 'POST' })
  await loadData()
}

async function refundOrder(id) {
  await adminRequest(`/admin/orders/${id}/refund`, { method: 'POST' })
  await loadData()
}

async function approvePayment(id) {
  const note = window.prompt('可选填写审核说明：', '审核通过')
  await adminRequest(`/admin/payments/${id}/approve`, {
    method: 'POST',
    body: JSON.stringify({ note: note || '' }),
  })
  await loadData()
}

async function rejectPayment(id) {
  const note = window.prompt('请输入驳回原因：', '审核驳回')
  await adminRequest(`/admin/payments/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify({ note: note || '审核驳回' }),
  })
  await loadData()
}

onMounted(async () => {
  syncBodyAuth()
  setView('overview')

  if (autoLoginUser && !state.token) {
    state.loginForm.username = autoLoginUser
    state.loginForm.password = '12345'
    await loginAdmin()
    return
  }

  if (state.token) {
    await loadData()
    return
  }

  setAdminLocked(true)
  setMessage('请输入管理员账号并登录后继续查看运营数据。', '未登录')
})
</script>

<template>
  <section v-show="state.isLocked" class="admin-login-shell">
    <div class="admin-login-card">
      <div class="brand-block brand-block-dark">
        <div class="brand-logo">CF</div>
        <div>
          <h1>CourtFlow</h1>
          <p>场地预约管理后台</p>
        </div>
      </div>
      <div class="login-copy">
        <h2>管理后台登录</h2>
      </div>
      <div class="form-grid auth-form-grid">
        <label class="field">
          <span>管理账号</span>
          <input v-model="state.loginForm.username" class="field-input" type="text" placeholder="请输入管理账号">
        </label>
        <label class="field">
          <span>登录密码</span>
          <input v-model="state.loginForm.password" class="field-input" type="password" placeholder="请输入登录密码">
        </label>
      </div>
      <div class="login-actions">
        <button class="primary-btn icon-btn" type="button" @click="loginAdmin">
          <LogIn class="btn-icon" />
          <span>进入管理台</span>
        </button>
      </div>
    </div>
  </section>

  <div v-show="!state.isLocked" class="admin-shell">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-logo">CF</div>
        <div>
          <h1>CourtFlow</h1>
          <p>场地预约管理后台</p>
        </div>
      </div>

      <nav class="sidebar-nav" aria-label="管理菜单">
        <a
          v-for="item in visibleMenuItems"
          :key="item.key"
          :class="{ active: state.activeView === item.key }"
          href="javascript:void(0)"
          @click="setView(item.key)"
        >
          <component :is="item.icon" class="nav-link__icon" />
          <span>{{ item.label }}</span>
        </a>
      </nav>
    </aside>

    <main class="admin-main">
      <header class="topbar">
        <div>
          <p class="eyebrow">管理后台</p>
          <h2>{{ currentViewTitle }}</h2>
          <p class="subtext">{{ state.message }}</p>
        </div>
        <div class="topbar-actions">
          <span class="status-pill">{{ state.statusText }}</span>
          <span class="admin-user-badge">{{ adminRoleLabel }}</span>
          <button class="ghost-btn icon-btn" type="button" @click="logoutAdmin">
            <LogOut class="btn-icon" />
            <span>退出登录</span>
          </button>
          <button class="primary-btn icon-btn" type="button" @click="loadData">
            <RefreshCw class="btn-icon" />
            <span>刷新</span>
          </button>
        </div>
      </header>

      <section v-if="isSuperAdmin" v-show="state.activeView === 'overview'" class="admin-view active">
        <section class="kpi-grid">
          <article v-for="item in kpis" :key="item.label" class="metric-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </section>

        <section class="overview-hero panel">
          <div class="overview-hero__copy">
            <p class="eyebrow">数据驾驶舱</p>
            <h3>今天的场馆运营一目了然</h3>
          </div>
          <div class="overview-hero__stats">
            <div v-for="item in overviewHeroStats" :key="item.label" class="overview-hero__stat">
              <i :style="{ backgroundColor: item.color }"></i>
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>

        <section class="overview-chart-grid">
          <article class="panel chart-panel chart-panel-wide">
            <div class="panel-head">
              <div>
                <h3>预约趋势</h3>
              </div>
            </div>
            <VChart class="chart-view" :option="reservationsTrendOption" autoresize />
          </article>

          <article class="panel chart-panel">
            <div class="panel-head">
              <div>
                <h3>预约状态分布</h3>
              </div>
            </div>
            <VChart class="chart-view" :option="reservationStatusOption" autoresize />
          </article>

          <article class="panel chart-panel">
            <div class="panel-head">
              <div>
                <h3>场馆资源排行</h3>
              </div>
            </div>
            <VChart class="chart-view" :option="venueResourceOption" autoresize />
          </article>
        </section>

        <section class="content-grid">
          <article class="panel">
            <div class="panel-head">
              <h3>场馆资源概览</h3>
            </div>
            <div class="panel-list">
              <div v-if="!venueSummaryList.length" class="empty-state">暂无场馆数据。</div>
              <article v-for="venue in venueSummaryList" :key="venue.id" class="summary-card">
                <strong>{{ venue.name }}</strong>
                <p>{{ venue.type }} · {{ venue.resourceCount }} 个资源 · {{ venue.priceText }}</p>
              </article>
            </div>
          </article>

          <article class="panel">
            <div class="panel-head">
              <h3>系统状态</h3>
            </div>
            <div class="status-list">
              <div v-for="[label, value] in systemStatusRows" :key="label" class="status-row">
                <strong>{{ label }}</strong>
                <span>{{ value }}</span>
              </div>
            </div>
          </article>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h3>最近预约记录</h3>
          </div>
          <div class="table table-reservations table-overview-reservations">
            <div class="table-head">
              <span>预约编号</span>
              <span>用户</span>
              <span>场馆 / 资源</span>
              <span>日期</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-if="!recentReservations.length" class="empty-state">暂无预约记录。</div>
            <div v-for="item in recentReservations" :key="item.id" class="table-row">
              <span>#{{ item.id }}</span>
              <span>{{ item.username }}</span>
              <span>{{ item.venueName }} / {{ item.resourceName }}</span>
              <span>{{ formatDate(item.slotDate, false) }}</span>
              <span><i class="status-tag" :class="statusMeta(item.status).klass">{{ item.statusLabel || statusMeta(item.status).label }}</i></span>
              <span class="table-actions">
                <button v-if="reservationActionButtons(item).canCancel" class="row-btn warn" type="button" @click="cancelReservation(item.id)">取消</button>
                <button v-if="reservationActionButtons(item).canCheckIn" class="row-btn primary" type="button" @click="checkInReservation(item.id)">到场签到</button>
                <button v-if="reservationActionButtons(item).canFinish" class="row-btn primary" type="button" @click="finishReservation(item.id)">结束使用</button>
              </span>
            </div>
          </div>
        </section>
      </section>

      <section v-show="state.activeView === 'venues'" class="admin-view active">
        <div class="management-grid">
          <article v-if="canEditVenueDetails" class="panel">
            <div class="panel-head">
              <h3>场馆表单</h3>
            </div>
            <form class="form-grid" @submit.prevent="saveVenue">
              <label class="field">
                <span>场馆名称</span>
                <input v-model="state.venueForm.name" class="field-input" type="text" placeholder="例如：北区羽毛球馆">
              </label>
              <label class="field">
                <span>场馆类型</span>
                <input v-model="state.venueForm.type" class="field-input" type="text" placeholder="例如：羽毛球">
              </label>
              <label class="field">
                <span>状态</span>
                <select v-model="state.venueForm.status" class="field-input">
                  <option :value="1">启用</option>
                  <option :value="0">禁用</option>
                </select>
              </label>
              <div class="form-actions">
                <button class="ghost-btn icon-btn" type="button" @click="resetVenueForm">
                  <RotateCcw class="btn-icon" />
                  <span>重置</span>
                </button>
                <button class="primary-btn icon-btn" type="submit">
                  <Save class="btn-icon" />
                  <span>保存场馆</span>
                </button>
              </div>
            </form>
          </article>

          <article class="panel">
            <div class="panel-head">
              <h3>场馆列表</h3>
            </div>
            <div class="list-stack">
              <div v-if="!state.venues.length" class="empty-state">暂无场馆数据。</div>
              <article v-for="venue in state.venues" :key="venue.id" class="summary-card">
                <div class="summary-head">
                  <div>
                    <strong>{{ venue.name }}</strong>
                    <p>{{ venue.type }} · {{ venue.resourceCount }} 个资源 · {{ venue.status === 1 ? '启用' : '禁用' }}</p>
                  </div>
                  <div class="table-actions">
                    <button v-if="isSuperAdmin" class="row-btn icon-btn" type="button" @click="fillVenueForm(venue); setView('venues')">
                      <Pencil class="btn-icon btn-icon-sm" />
                      <span>编辑</span>
                    </button>
                    <button
                      v-if="isVenueAdmin && venue.status !== 1"
                      class="row-btn primary icon-btn"
                      type="button"
                      @click="updateVenueStatus(venue.id, 1)"
                    >
                      <Check class="btn-icon btn-icon-sm" />
                      <span>开放场馆</span>
                    </button>
                    <button
                      v-if="isVenueAdmin && venue.status === 1"
                      class="row-btn warn icon-btn"
                      type="button"
                      @click="updateVenueStatus(venue.id, 0)"
                    >
                      <X class="btn-icon btn-icon-sm" />
                      <span>关闭场馆</span>
                    </button>
                    <button v-if="isSuperAdmin" class="row-btn danger icon-btn" type="button" @click="deleteVenue(venue.id)">
                      <Trash2 class="btn-icon btn-icon-sm" />
                      <span>删除</span>
                    </button>
                  </div>
                </div>
              </article>
            </div>
          </article>
        </div>
      </section>

      <section v-show="state.activeView === 'resources'" class="admin-view active">
        <div class="management-grid">
          <article class="panel">
            <div class="panel-head">
              <h3>资源表单</h3>
            </div>
            <form class="form-grid" @submit.prevent="saveResource">
              <label class="field">
                <span>所属场馆</span>
                <select v-model="state.resourceForm.venueId" class="field-input">
                  <option v-for="venue in venueOptions" :key="venue.id" :value="String(venue.id)">{{ venue.name }}</option>
                </select>
              </label>
              <label class="field">
                <span>资源名称</span>
                <input v-model="state.resourceForm.name" class="field-input" type="text" placeholder="例如：A1 场地">
              </label>
              <label class="field">
                <span>资源类型</span>
                <select v-model="state.resourceForm.resourceType" class="field-input">
                  <option :value="1">羽毛球</option>
                  <option :value="2">篮球</option>
                  <option :value="3">网球</option>
                  <option :value="4">足球</option>
                  <option :value="5">乒乓球</option>
                  <option :value="6">游泳</option>
                  <option :value="7">综合场地</option>
                </select>
              </label>
              <label class="field">
                <span>容纳人数</span>
                <input v-model="state.resourceForm.capacity" class="field-input" type="number" min="1">
              </label>
              <label class="field">
                <span>单价</span>
                <input v-model="state.resourceForm.price" class="field-input" type="number" min="0">
              </label>
              <label class="field">
                <span>时间粒度(分钟)</span>
                <input v-model="state.resourceForm.unitMinutes" class="field-input" type="number" min="5">
              </label>
              <label class="field">
                <span>状态</span>
                <select v-model="state.resourceForm.status" class="field-input">
                  <option :value="1">启用</option>
                  <option :value="0">禁用</option>
                </select>
              </label>
              <div class="form-actions">
                <button class="ghost-btn icon-btn" type="button" @click="resetResourceForm">
                  <RotateCcw class="btn-icon" />
                  <span>重置</span>
                </button>
                <button class="primary-btn icon-btn" type="submit">
                  <Save class="btn-icon" />
                  <span>保存资源</span>
                </button>
              </div>
            </form>
          </article>

          <article class="panel">
            <div class="panel-head">
              <h3>资源列表</h3>
            </div>
            <div class="table table-resources">
              <div class="table-head">
                <span>资源编号</span>
                <span>所属场馆</span>
                <span>资源信息</span>
                <span>价格 / 粒度</span>
                <span>状态</span>
                <span>操作</span>
              </div>
              <div v-if="!state.resources.length" class="empty-state">暂无资源数据。</div>
              <div v-for="item in state.resources" :key="item.id" class="table-row">
                <span>#{{ item.id }}</span>
                <span>{{ item.venueName }}</span>
                <span>{{ item.name }} · {{ item.resourceTypeLabel }} · 容量 {{ item.capacity }}</span>
                <span>¥{{ item.price }} / {{ item.unitMinutes }} 分钟</span>
                <span><i class="status-tag" :class="item.status === 1 ? 'live' : 'warn'">{{ item.statusLabel }}</i></span>
                <span class="table-actions">
                  <button class="row-btn icon-btn" type="button" @click="fillResourceForm(item); setView('resources')">
                    <Pencil class="btn-icon btn-icon-sm" />
                    <span>编辑</span>
                  </button>
                  <button class="row-btn danger icon-btn" type="button" @click="deleteResource(item.id)">
                    <Trash2 class="btn-icon btn-icon-sm" />
                    <span>删除</span>
                  </button>
                </span>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-show="state.activeView === 'reservations'" class="admin-view active">
        <section class="panel">
          <div class="panel-head">
            <h3>预约管理</h3>
          </div>
            <div class="filter-row">
            <label class="field inline-field">
              <span>预约状态</span>
              <select v-model="state.reservationFilterStatus" class="field-input">
                <option value="">全部</option>
                <option value="0">排队中</option>
                <option value="1">待使用</option>
                <option value="5">使用中</option>
                <option value="2">已取消</option>
                <option value="4">已完成</option>
              </select>
            </label>
            <label class="field inline-field">
              <span>预约日期</span>
              <input v-model="state.reservationFilterDate" class="field-input" type="date">
            </label>
            <label class="field inline-field wide-field">
              <span>关键字</span>
              <input v-model="state.reservationFilterKeyword" class="field-input" type="text" placeholder="用户 / 场馆 / 资源 / 编号" @keydown.enter.prevent="applyReservationFilter">
            </label>
              <button class="ghost-btn icon-btn" type="button" @click="applyReservationFilter">
                <Filter class="btn-icon" />
                <span>应用筛选</span>
              </button>
          </div>
          <div class="table table-reservations">
            <div class="table-head">
              <span>预约编号</span>
              <span>用户</span>
              <span>场馆 / 资源</span>
              <span>日期 / 时段</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-if="!state.reservations.length" class="empty-state">暂无预约数据。</div>
            <div v-for="item in state.reservations" :key="item.id" class="table-row">
              <span>#{{ item.id }}</span>
              <span>{{ item.username }}</span>
              <span>{{ item.venueName }} / {{ item.resourceName }}</span>
              <span>{{ formatDate(item.slotDate, false) }} · {{ formatReservationSlot(item) }}</span>
              <span><i class="status-tag" :class="statusMeta(item.status).klass">{{ item.statusLabel || statusMeta(item.status).label }}</i></span>
              <span class="table-actions">
                <button v-if="reservationActionButtons(item).canCancel" class="row-btn warn icon-btn" type="button" @click="cancelReservation(item.id)">
                  <X class="btn-icon btn-icon-sm" />
                  <span>取消</span>
                </button>
                <button v-if="reservationActionButtons(item).canCheckIn" class="row-btn primary icon-btn" type="button" @click="checkInReservation(item.id)">
                  <Check class="btn-icon btn-icon-sm" />
                  <span>到场签到</span>
                </button>
                <button v-if="reservationActionButtons(item).canFinish" class="row-btn primary icon-btn" type="button" @click="finishReservation(item.id)">
                  <Check class="btn-icon btn-icon-sm" />
                  <span>结束使用</span>
                </button>
              </span>
            </div>
          </div>
        </section>
      </section>

      <section v-if="isSuperAdmin" v-show="state.activeView === 'users'" class="admin-view active">
        <section class="panel">
          <div class="panel-head">
            <h3>用户信息</h3>
          </div>
          <div class="table table-users">
            <div class="table-head">
              <span>用户编号</span>
              <span>账号信息</span>
              <span>角色</span>
              <span>状态 / 余额</span>
              <span>预约 / 订单</span>
              <span>操作</span>
            </div>
            <div v-if="!state.users.length" class="empty-state">暂无用户数据。</div>
            <div v-for="item in state.users" :key="item.id" class="table-row">
              <span>#{{ item.id }}</span>
              <span>{{ item.username }}</span>
              <span>
                <select v-model="state.userEdits[item.id].role" class="row-select">
                  <option value="USER">USER</option>
                  <option value="ADMIN">ADMIN</option>
                  <option value="VENUE_ADMIN">VENUE_ADMIN</option>
                </select>
              </span>
              <span class="user-inline-edit">
                <select v-model="state.userEdits[item.id].status" class="row-select">
                  <option :value="1">启用</option>
                  <option :value="0">禁用</option>
                </select>
                <input v-model="state.userEdits[item.id].balance" class="row-input" type="number" min="0">
              </span>
              <span>{{ item.reservationCount }} / {{ item.orderCount || 0 }}</span>
              <span class="table-actions">
                <button class="row-btn primary icon-btn" type="button" @click="saveUser(item.id)">
                  <Save class="btn-icon btn-icon-sm" />
                  <span>保存</span>
                </button>
              </span>
            </div>
          </div>
        </section>
      </section>

      <section v-if="isSuperAdmin" v-show="state.activeView === 'orders'" class="admin-view active">
        <section class="panel">
          <div class="panel-head">
            <h3>订单管理</h3>
          </div>
          <div class="table table-orders">
            <div class="table-head">
              <span>订单号</span>
              <span>用户</span>
              <span>金额</span>
              <span>预约信息</span>
              <span>状态</span>
              <span>操作</span>
            </div>
            <div v-if="!state.orders.length" class="empty-state">暂无订单数据。</div>
            <div v-for="item in state.orders" :key="item.id" class="table-row">
              <span>{{ item.orderNo }}</span>
              <span>{{ item.username }}</span>
              <span>¥{{ ((item.totalAmount || 0) / 100).toFixed(2) }}</span>
              <span>{{ item.reservationId ? `预约 #${item.reservationId}` : '--' }}</span>
              <span><i class="status-tag" :class="orderStatusClass(item)">{{ item.statusLabel }}</i></span>
              <span class="table-actions">
                <button v-if="item.status === 0" class="row-btn warn icon-btn" type="button" @click="closeOrder(item.id)">
                  <X class="btn-icon btn-icon-sm" />
                  <span>关闭</span>
                </button>
                <button v-if="item.status === 1" class="row-btn danger icon-btn" type="button" @click="refundOrder(item.id)">
                  <RotateCcw class="btn-icon btn-icon-sm" />
                  <span>退款</span>
                </button>
              </span>
            </div>
          </div>
        </section>
      </section>

      <section v-if="isSuperAdmin" v-show="state.activeView === 'payments'" class="admin-view active">
        <section class="panel">
          <div class="panel-head">
            <h3>支付记录</h3>
          </div>
          <div class="table table-payments">
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
            <div v-if="!state.payments.length" class="empty-state">暂无支付记录。</div>
            <div v-for="item in state.payments" :key="item.id" class="table-row">
              <span>#{{ item.id }}</span>
              <span>{{ item.bizTypeLabel || '--' }}</span>
              <span>{{ item.orderNo || `订单 #${item.orderId}` }}</span>
              <span>{{ item.payChannelLabel || `渠道 ${item.payChannel}` }}</span>
              <span>¥{{ ((item.payAmount || 0) / 100).toFixed(2) }}</span>
              <span><i class="status-tag" :class="paymentStatusClass(item)">{{ item.payStatusLabel || '处理中' }}</i></span>
              <span>{{ item.statusNote || '--' }}</span>
              <span>{{ formatDateTime(item.paidAt || item.processedAt || item.createdAt) }}</span>
              <span class="table-actions">
                <button v-if="item.reviewable" class="row-btn primary icon-btn" type="button" @click="approvePayment(item.id)">
                  <Check class="btn-icon btn-icon-sm" />
                  <span>通过</span>
                </button>
                <button v-if="item.reviewable" class="row-btn danger icon-btn" type="button" @click="rejectPayment(item.id)">
                  <X class="btn-icon btn-icon-sm" />
                  <span>驳回</span>
                </button>
              </span>
            </div>
          </div>
        </section>
      </section>
    </main>
  </div>
</template>
