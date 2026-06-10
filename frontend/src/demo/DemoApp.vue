<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  CalendarDays,
  Check,
  ChevronLeft,
  Home,
  LogIn,
  LogOut,
  RefreshCw,
  Save,
  Search,
  Settings,
  User,
  UserPlus,
  X,
} from 'lucide-vue-next'
import { apiRequest } from '../shared/api'
import { DEMO_TOKEN_STORAGE_KEY } from '../shared/constants'
import {
  formatDate,
  formatInputDate,
  formatMinutes,
  formatSlotRange,
  rangeUnits,
  toMinutes,
} from '../shared/formatters'

const screens = ['home', 'booking', 'booking-detail', 'orders', 'settings']
const query = new URLSearchParams(window.location.search)
const requestedScreen = query.get('screen')

const state = reactive({
  token: localStorage.getItem(DEMO_TOKEN_STORAGE_KEY) || '',
  profile: null,
  venues: [],
  recommendations: [],
  reservations: [],
  ordersFilter: 'all',
  authMode: 'login',
  bookingSearchKeyword: '',
  bookingTypeFilter: 'all',
  selectedVenueId: null,
  selectedResourceId: null,
  availability: null,
  selectionAnchorUnit: null,
  activeScreen: screens.includes(requestedScreen) ? requestedScreen : 'home',
  bookingDate: '',
  bookingSize: 4,
  toastMessage: '',
  toastVisible: false,
  toastActive: false,
  authForm: {
    username: '',
    nickname: '',
    password: '',
    confirmPassword: '',
  },
  settingsForm: {
    nickname: '',
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  },
  authMessage: '登录只需要输入用户名和密码。',
  settingsProfileMessage: '用户名用于登录，用户昵称会显示在前端页面。',
  settingsPasswordMessage: '密码修改后，下次登录将使用新密码。',
  welcomeTitle: '选择今天想去的场地',
  welcomeSubtitle: '正在同步场馆与推荐数据',
  bookingStatusMessage: '先选择场馆，再选择场地、日期和时间段',
  loadingError: '',
})

const selectedSlots = ref([])
let toastTimer = null

const isLoggedIn = computed(() => Boolean(state.token))

const displayName = computed(() => {
  const candidates = [
    state.profile?.nickname?.trim(),
    state.profile?.username?.trim(),
  ]
  return candidates.find((value) => value && !/^\?+$/.test(value)) || ''
})

const avatarText = computed(() => {
  if (!displayName.value) {
    return 'C'
  }
  return displayName.value.charAt(0).toUpperCase()
})

const authText = computed(() => (state.profile ? displayName.value : 'CourtFlow'))
const ordersTitle = computed(() => (isLoggedIn.value ? '我的预约' : '我的'))

const quickMetrics = computed(() => {
  const totalResources = state.venues.reduce((sum, venue) => sum + (venue.resourceCount || venue.resources?.length || 0), 0)
  return [
    { label: '开放场馆', value: `${state.venues.length}` },
    { label: '可约场地', value: `${totalResources}` },
    { label: '预约记录', value: `${state.reservations.length}` },
  ]
})

const selectedVenue = computed(() =>
  state.venues.find((venue) => venue.id === state.selectedVenueId) || null,
)

const selectedResourceBundle = computed(() => {
  const venue = selectedVenue.value
  if (!venue) {
    return null
  }
  const resource = (venue.resources || []).find((item) => item.id === state.selectedResourceId)
  return resource ? { venue, resource } : null
})

const selectedVenueResources = computed(() => selectedVenue.value?.resources || [])

const bookingTypes = computed(() => [...new Set(state.venues.map((venue) => venue.type))])

const filteredBookingVenues = computed(() => {
  const keyword = state.bookingSearchKeyword.trim().toLowerCase()
  return state.venues.filter((venue) => {
    const matchType = state.bookingTypeFilter === 'all' || String(venue.type).includes(state.bookingTypeFilter)
    const matchKeyword = !keyword || String(venue.name).toLowerCase().includes(keyword)
    return matchType && matchKeyword
  })
})

const recommendationCards = computed(() => state.recommendations.slice(0, 1))

const selectedSlotsSorted = computed(() => [...selectedSlots.value].sort((left, right) => left - right))

const selectedUnitMinutes = computed(
  () => selectedResourceBundle.value?.resource?.unitMinutes || state.availability?.unitMinutes || 10,
)

const summaryPrice = computed(() => {
  const price = selectedResourceBundle.value?.resource?.price || 0
  return price * selectedSlotsSorted.value.length
})

const canSubmitBooking = computed(() =>
  Boolean(state.selectedVenueId)
  && Boolean(selectedResourceBundle.value)
  && Boolean(state.bookingDate)
  && selectedSlotsSorted.value.length > 0,
)

const bookingActionVisible = computed(
  () => state.activeScreen === 'booking-detail' && Boolean(state.selectedVenueId),
)

const filteredReservations = computed(() => {
  if (state.ordersFilter === 'active') {
    return state.reservations.filter((item) => [0, 1, 5].includes(item.status))
  }
  if (state.ordersFilter === 'done') {
    return state.reservations.filter((item) => item.status === 4)
  }
  if (state.ordersFilter === 'cancelled') {
    return state.reservations.filter((item) => item.status === 2)
  }
  return state.reservations
})

const visibleAvailabilitySlots = computed(() => {
  const slots = state.availability?.slots || []
  const unitMinutes = selectedUnitMinutes.value
  const startUnit = Math.floor((8 * 60) / unitMinutes)
  const endUnit = Math.floor((22 * 60) / unitMinutes)
  const visible = slots.filter((item) => item.slotUnit >= startUnit && item.slotUnit <= endUnit)
  return visible.length ? visible : slots
})

const heroReasonText = computed(() => {
  const top = recommendationCards.value[0]
  return top ? top.reasonList.join('；') : '可以稍后刷新推荐或直接进入预约页选择场地。'
})

const heroResourceText = computed(() => '猜你喜欢')

function getTodayDateString() {
  return formatInputDate(new Date())
}

function getMaxBookingDateString() {
  const maxDate = new Date()
  maxDate.setDate(maxDate.getDate() + 14)
  return formatInputDate(maxDate)
}

function isPastBookingDate(dateValue) {
  return Boolean(dateValue) && dateValue < getTodayDateString()
}

function isTodayBookingDate(dateValue) {
  return Boolean(dateValue) && dateValue === getTodayDateString()
}

function isBeyondBookingWindow(dateValue) {
  return Boolean(dateValue) && dateValue > getMaxBookingDateString()
}

function getCurrentMinutesOfDay() {
  const now = new Date()
  return now.getHours() * 60 + now.getMinutes()
}

function hideToast() {
  if (toastTimer) {
    clearTimeout(toastTimer)
    toastTimer = null
  }
  state.toastActive = false
  window.setTimeout(() => {
    state.toastVisible = false
  }, 240)
}

function showToast(message, duration = 3200) {
  if (!message) {
    hideToast()
    return
  }
  if (toastTimer) {
    clearTimeout(toastTimer)
  }
  state.toastMessage = message
  state.toastVisible = true
  requestAnimationFrame(() => {
    state.toastActive = true
  })
  toastTimer = window.setTimeout(() => {
    state.toastActive = false
    toastTimer = window.setTimeout(() => {
      state.toastVisible = false
      toastTimer = null
    }, 240)
  }, duration)
}

function showTransientBookingMessage(message, duration = 3200) {
  showToast(message, duration)
}

function setAuthMode(mode) {
  state.authMode = mode === 'register' ? 'register' : 'login'
  state.authMessage = state.authMode === 'register'
    ? '注册时需要填写用户名、用户昵称和密码，注册后会自动登录。'
    : '登录只需要输入用户名和密码。'
  if (state.authMode !== 'register') {
    state.authForm.nickname = ''
    state.authForm.confirmPassword = ''
  }
}

function getRoleLabel(role) {
  return role === 'ADMIN' ? '管理员' : '用户'
}

function isTabActive(screenKey) {
  if (screenKey === 'booking') {
    return state.activeScreen === 'booking' || state.activeScreen === 'booking-detail'
  }
  if (screenKey === 'orders') {
    return state.activeScreen === 'orders' || state.activeScreen === 'settings'
  }
  return state.activeScreen === screenKey
}

function setActiveScreen(screenKey) {
  if (screenKey === 'settings' && !isLoggedIn.value) {
    state.activeScreen = 'orders'
    return
  }
  if (screenKey === 'booking-detail' && !state.selectedVenueId) {
    state.activeScreen = 'booking'
    return
  }
  state.activeScreen = screenKey
}

function resetSlotSelection() {
  selectedSlots.value = []
  state.selectionAnchorUnit = null
}

function ensureBookingDateIsValid() {
  const today = getTodayDateString()
  const maxDate = getMaxBookingDateString()

  if (isPastBookingDate(state.bookingDate)) {
    state.bookingDate = today
    resetSlotSelection()
    showTransientBookingMessage('预约日期不能早于今天，已自动调整。')
  }

  if (isBeyondBookingWindow(state.bookingDate)) {
    state.bookingDate = maxDate
    resetSlotSelection()
    showTransientBookingMessage('仅支持预约14天以内的场馆！')
  }
}

function syncSelectedResource() {
  const venue = selectedVenue.value
  if (!venue) {
    state.selectedResourceId = null
    state.availability = null
    return
  }
  const hasCurrentResource = (venue.resources || []).some((item) => item.id === state.selectedResourceId)
  if (!hasCurrentResource) {
    state.selectedResourceId = venue.resources?.[0]?.id || null
  }
}

function getAvailabilitySlot(unit) {
  return (state.availability?.slots || []).find((item) => item.slotUnit === unit) || null
}

function isSlotExpired(slot) {
  if (!slot || !isTodayBookingDate(state.bookingDate)) {
    return false
  }
  return toMinutes(slot.startTime) < getCurrentMinutesOfDay()
}

function isSlotAvailableForSize(slot) {
  const size = Number(state.bookingSize || 1)
  return Boolean(slot)
    && !isSlotExpired(slot)
    && slot.status !== 2
    && slot.remainingCapacity >= size
}

function getSlotStateText(slot, disabled) {
  if (!disabled) {
    return `余 ${slot.remainingCapacity}`
  }
  if (isSlotExpired(slot)) {
    return '已过期'
  }
  return '已满'
}

function isContinuousUnits(units) {
  if (!units.length) {
    return false
  }
  for (let index = 1; index < units.length; index += 1) {
    if (units[index] !== units[index - 1] + 1) {
      return false
    }
  }
  return true
}

function validateSelectedSlots() {
  if (!selectedSlots.value.length) {
    return
  }
  const sorted = [...selectedSlots.value].sort((left, right) => left - right)
  const valid = sorted.every((unit) => isSlotAvailableForSize(getAvailabilitySlot(unit)))
  if (!valid || !isContinuousUnits(sorted)) {
    resetSlotSelection()
    state.bookingStatusMessage = '原已选时段已变化，请重新选择。'
  }
}

async function loadAvailability(silent = false) {
  ensureBookingDateIsValid()
  if (!state.selectedVenueId || !state.selectedResourceId || !state.bookingDate) {
    state.availability = null
    return
  }
  try {
    state.availability = await apiRequest(
      `/reservation/availability?resourceId=${state.selectedResourceId}&slotDate=${state.bookingDate}T00:00:00`,
      {},
      state.token,
    )
    validateSelectedSlots()
  } catch (error) {
    state.availability = null
    if (!silent) {
      state.bookingStatusMessage = error.message
    }
  }
}

function pickSlotUnit(unit) {
  const slot = getAvailabilitySlot(unit)
  if (!isSlotAvailableForSize(slot)) {
    return
  }
  if (state.selectionAnchorUnit === null || selectedSlots.value.length > 1) {
    state.selectionAnchorUnit = unit
    selectedSlots.value = [unit]
    return
  }
  if (state.selectionAnchorUnit === unit) {
    resetSlotSelection()
    return
  }
  const units = rangeUnits(
    Math.min(state.selectionAnchorUnit, unit),
    Math.max(state.selectionAnchorUnit, unit),
  )
  const canSelectRange = units.every((slotUnit) => isSlotAvailableForSize(getAvailabilitySlot(slotUnit)))
  if (!canSelectRange) {
    state.bookingStatusMessage = '所选区间包含已满时段，请重新选择。'
    selectedSlots.value = [state.selectionAnchorUnit]
    return
  }
  selectedSlots.value = units
}

async function selectVenue(venueId) {
  state.selectedVenueId = venueId
  syncSelectedResource()
  resetSlotSelection()
  await loadAvailability(true)
  setActiveScreen('booking-detail')
}

function getResourceMeta(resourceId) {
  for (const venue of state.venues) {
    const resource = (venue.resources || []).find((item) => item.id === resourceId)
    if (resource) {
      return {
        venueName: venue.name,
        resourceName: resource.name,
        unitMinutes: resource.unitMinutes || 10,
      }
    }
  }
  return {
    venueName: '',
    resourceName: '',
    unitMinutes: 10,
  }
}

function reservationStatusMeta(status) {
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
  return { label: '排队中', klass: 'live' }
}

async function loginUser(username, password, successMessage = '') {
  const token = await apiRequest('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
  state.token = token
  localStorage.setItem(DEMO_TOKEN_STORAGE_KEY, token)
  await loadData()
  if (successMessage) {
    showToast(successMessage)
  }
  if (requestedScreen && screens.includes(requestedScreen)) {
    setActiveScreen(requestedScreen)
  } else {
    setActiveScreen('orders')
  }
}

async function submitAuthForm() {
  const username = state.authForm.username.trim()
  const nickname = state.authForm.nickname.trim()
  const password = state.authForm.password
  const confirmPassword = state.authForm.confirmPassword
  const isRegister = state.authMode === 'register'

  if (!username) {
    state.authMessage = '请输入用户名。'
    return
  }
  if (isRegister && !nickname) {
    state.authMessage = '请输入用户昵称。'
    return
  }
  if (!password.trim()) {
    state.authMessage = '请输入密码。'
    return
  }
  if (password.length < 5) {
    state.authMessage = '密码至少 5 位。'
    return
  }
  if (isRegister && password !== confirmPassword) {
    state.authMessage = '两次输入的密码不一致。'
    return
  }

  state.authMessage = isRegister ? '注册中...' : '登录中...'

  try {
    if (isRegister) {
      await apiRequest('/auth/register', {
        method: 'POST',
        body: JSON.stringify({ username, nickname, password }),
      })
      await loginUser(username, password, '注册成功，已自动登录。')
    } else {
      await loginUser(username, password, '登录成功。')
    }
    state.authForm = {
      username: '',
      nickname: '',
      password: '',
      confirmPassword: '',
    }
    setAuthMode(state.authMode)
  } catch (error) {
    state.authMessage = error.message
  }
}

function logoutUser() {
  state.token = ''
  state.profile = null
  state.reservations = []
  localStorage.removeItem(DEMO_TOKEN_STORAGE_KEY)
  window.location.href = window.location.pathname
}

async function saveProfileSettings() {
  if (!isLoggedIn.value) {
    setActiveScreen('orders')
    return
  }
  const nickname = state.settingsForm.nickname.trim()
  if (!nickname) {
    state.settingsProfileMessage = '请输入用户昵称。'
    return
  }
  if (nickname.length < 2) {
    state.settingsProfileMessage = '用户昵称至少 2 位。'
    return
  }

  state.settingsProfileMessage = '保存中...'

  try {
    const profile = await apiRequest('/user/profile', {
      method: 'PUT',
      body: JSON.stringify({ nickname }),
    }, state.token)
    state.profile = profile
    state.settingsForm.nickname = profile.nickname || ''
    state.settingsProfileMessage = '用户名用于登录，用户昵称会显示在前端页面。'
    showToast('用户昵称已更新。')
  } catch (error) {
    state.settingsProfileMessage = error.message
  }
}

async function savePasswordSettings() {
  if (!isLoggedIn.value) {
    setActiveScreen('orders')
    return
  }
  const { currentPassword, newPassword, confirmPassword } = state.settingsForm
  if (!currentPassword.trim()) {
    state.settingsPasswordMessage = '请输入当前密码。'
    return
  }
  if (!newPassword.trim()) {
    state.settingsPasswordMessage = '请输入新密码。'
    return
  }
  if (newPassword.length < 5) {
    state.settingsPasswordMessage = '新密码至少 5 位。'
    return
  }
  if (newPassword !== confirmPassword) {
    state.settingsPasswordMessage = '两次输入的新密码不一致。'
    return
  }

  state.settingsPasswordMessage = '修改中...'

  try {
    await apiRequest('/user/password', {
      method: 'PUT',
      body: JSON.stringify({
        currentPassword,
        newPassword,
      }),
    }, state.token)
    state.settingsForm.currentPassword = ''
    state.settingsForm.newPassword = ''
    state.settingsForm.confirmPassword = ''
    state.settingsPasswordMessage = '密码修改后，下次登录将使用新密码。'
    showToast('密码已更新。')
  } catch (error) {
    state.settingsPasswordMessage = error.message
  }
}

async function submitBooking() {
  if (!selectedResourceBundle.value) {
    state.bookingStatusMessage = '请先选择场馆和场地。'
    return
  }
  if (!state.bookingDate) {
    state.bookingStatusMessage = '请选择预约日期。'
    return
  }
  if (!selectedSlotsSorted.value.length) {
    state.bookingStatusMessage = '请至少选择一个时间段。'
    return
  }
  if (!isContinuousUnits(selectedSlotsSorted.value)) {
    state.bookingStatusMessage = '预约时段必须连续。'
    return
  }
  if (!isLoggedIn.value) {
    state.bookingStatusMessage = '请先到“我的”页面登录后再提交预约。'
    setActiveScreen('orders')
    return
  }

  try {
    state.bookingStatusMessage = '预约提交中...'
    const id = await apiRequest('/reservation/apply', {
      method: 'POST',
      body: JSON.stringify({
        venueId: selectedResourceBundle.value.venue.id,
        resourceId: selectedResourceBundle.value.resource.id,
        slotDate: `${state.bookingDate}T00:00:00`,
        startUnit: selectedSlotsSorted.value[0],
        endUnit: selectedSlotsSorted.value[selectedSlotsSorted.value.length - 1],
        size: Number(state.bookingSize || 1),
      }),
    }, state.token)
    state.bookingStatusMessage = `预约成功，记录编号 #${id}`
    resetSlotSelection()
    await loadData()
    setActiveScreen('orders')
  } catch (error) {
    state.bookingStatusMessage = error.message
  }
}

async function cancelReservation(id) {
  try {
    await apiRequest(`/reservation/${id}/cancel`, { method: 'POST' }, state.token)
    await loadData()
    showToast('预约已取消。')
  } catch (error) {
    showToast(error.message || '取消预约失败。')
  }
}

function updateWelcomeCopy() {
  if (isLoggedIn.value && state.profile) {
    state.welcomeTitle = `${displayName.value || state.profile.username}，今天想预约哪块场地？`
    state.welcomeSubtitle = `当前可查看 ${state.venues.length} 个场馆、${state.reservations.length} 条预约记录`
  } else {
    state.welcomeTitle = '选择今天想去的场地'
    state.welcomeSubtitle = `当前开放 ${state.venues.length} 个场馆，可先浏览场地信息`
  }
}

async function loadData() {
  try {
    state.loadingError = ''
    const [venues, recommendations] = await Promise.all([
      apiRequest('/venue/list', {}, state.token),
      apiRequest('/recommendation/venues', {
        method: 'POST',
        body: JSON.stringify({
          sportKeyword: '羽毛球',
          preferredUnitMinutes: 10,
          expectedPeopleCount: 4,
          maxBudget: 60,
          preferLowPrice: true,
          expectedStartUnit: 114,
          expectedEndUnit: 125,
          topN: 1,
        }),
      }, state.token),
    ])

    state.venues = venues
    state.recommendations = recommendations

    if (state.selectedVenueId && !state.venues.some((venue) => venue.id === state.selectedVenueId)) {
      state.selectedVenueId = null
    }
    syncSelectedResource()

    if (isLoggedIn.value) {
      const [profile, reservations] = await Promise.all([
        apiRequest('/user/profile', {}, state.token),
        apiRequest('/reservation/my?pageNumber=1&pageSize=10', {}, state.token),
      ])
      state.profile = profile
      state.reservations = reservations.records || []
      state.settingsForm.nickname = profile.nickname || ''
    } else {
      state.profile = null
      state.reservations = []
      state.settingsForm.nickname = ''
    }

    updateWelcomeCopy()
    await loadAvailability(true)

    if (requestedScreen && screens.includes(requestedScreen)) {
      setActiveScreen(requestedScreen)
    }
  } catch (error) {
    state.loadingError = error.message
    state.welcomeSubtitle = error.message
    state.bookingStatusMessage = error.message
  }
}

function openOrdersFromAvatar() {
  setActiveScreen('orders')
}

async function openRecommendedVenue(venueName) {
  const venue = state.venues.find((item) => item.name === venueName)
  if (!venue) {
    return
  }
  if (!isLoggedIn.value) {
    setActiveScreen('orders')
    return
  }
  await selectVenue(venue.id)
}

async function openVenueBooking(venueId) {
  if (!isLoggedIn.value) {
    setActiveScreen('orders')
    return
  }
  await selectVenue(venueId)
}

async function onBookingDateChange() {
  resetSlotSelection()
  await loadAvailability()
}

async function onBookingSizeChange() {
  validateSelectedSlots()
}

onMounted(async () => {
  document.body.classList.add('app-body')
  setAuthMode('login')
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  state.bookingDate = formatInputDate(tomorrow)
  await loadData()
})
</script>

<template>
  <main class="app-preview">
    <section class="phone-shell" aria-label="CourtFlow 用户端">
      <div class="phone-notch" aria-hidden="true"></div>
      <div class="phone-status">
        <span>9:41</span>
        <span>CourtFlow</span>
        <span>{{ authText }}</span>
      </div>

      <div
        v-show="state.toastVisible"
        :class="['app-toast', { active: state.toastActive }]"
        aria-live="polite"
        aria-atomic="true"
      >
        <div class="app-toast__content">{{ state.toastMessage }}</div>
      </div>

      <div class="app-content">
        <section v-show="state.activeScreen === 'home'" class="app-screen active">
          <header class="home-header">
            <div>
              <h1>{{ state.welcomeTitle }}</h1>
              <p class="subtext">{{ state.welcomeSubtitle }}</p>
            </div>
            <button class="avatar-chip" type="button" aria-label="进入我的页面" @click="openOrdersFromAvatar">
              {{ avatarText }}
            </button>
          </header>

          <section class="hero-panel">
            <div class="hero-copy">
              <h2>{{ heroResourceText }}</h2>
              <p>{{ heroReasonText }}</p>
              <div class="hero-recommendation-list">
                <div v-if="state.loadingError" class="empty-state hero-empty-state">{{ state.loadingError }}</div>
                <div v-else-if="!recommendationCards.length" class="empty-state hero-empty-state">暂无推荐结果。</div>
                <article
                  v-for="item in recommendationCards"
                  :key="`${item.venueId}-${item.resourceId}`"
                  class="hero-recommendation-card"
                  @click="openRecommendedVenue(item.venueName)"
                >
                  <div class="hero-recommendation-head">
                    <strong>{{ item.venueName }}</strong>
                    <span>{{ item.resourceName }}</span>
                  </div>
                  <p>{{ item.reasonList.join('；') }}</p>
                </article>
              </div>
              <div class="hero-actions">
                <button class="primary-btn hero-primary-btn icon-btn" type="button" @click="setActiveScreen(isLoggedIn ? 'booking' : 'orders')">
                  <CalendarDays class="btn-icon" />
                  <span>立即预约</span>
                </button>
              </div>
            </div>
          </section>

          <section class="metric-grid">
            <article v-for="item in quickMetrics" :key="item.label" class="metric-card">
              <strong>{{ item.value }}</strong>
              <span>{{ item.label }}</span>
            </article>
          </section>

          <section class="panel-card">
            <div class="section-head">
              <h3>热门场馆</h3>
              <button class="text-btn icon-btn" type="button" @click="loadData">
                <RefreshCw class="btn-icon btn-icon-sm" />
                <span>刷新</span>
              </button>
            </div>
            <div class="venue-list">
              <div v-if="state.loadingError" class="empty-state">{{ state.loadingError }}</div>
              <div v-else-if="!state.venues.length" class="empty-state">当前没有可用场馆数据。</div>
              <article
                v-for="venue in state.venues"
                :key="venue.id"
                class="venue-card interactive-card"
                @click="openVenueBooking(venue.id)"
              >
                <div class="venue-body">
                  <div class="venue-title">
                    <strong>{{ venue.name }}</strong>
                    <span>{{ venue.resourceCount }} 个场地</span>
                  </div>
                  <p>{{ venue.type }} · {{ venue.resources?.[0]?.unitMinutes ?? '--' }} 分钟/段</p>
                  <div class="venue-meta">
                    <span>{{ venue.resources?.[0]?.name || '暂无场地' }}</span>
                    <span>{{ venue.resources?.[0] ? `¥${venue.resources[0].price}` : '待定' }}</span>
                  </div>
                </div>
                <div class="card-link">去预约</div>
              </article>
            </div>
          </section>
        </section>

        <section v-show="state.activeScreen === 'booking'" class="app-screen active">
          <header class="page-header">
            <div>
              <h2>选择场馆</h2>
            </div>
            <div class="page-header-actions">
              <button class="avatar-chip" type="button" aria-label="进入我的页面" @click="openOrdersFromAvatar">
                {{ avatarText }}
              </button>
            </div>
          </header>

          <section class="booking-toolbar">
            <label class="search-field search-field-with-icon">
              <Search class="field-leading-icon" />
              <input v-model="state.bookingSearchKeyword" type="search" placeholder="搜索场馆名称">
            </label>
            <div class="filter-bar booking-filter-bar">
              <button
                class="filter-chip"
                :class="{ active: state.bookingTypeFilter === 'all' }"
                type="button"
                @click="state.bookingTypeFilter = 'all'"
              >
                全部
              </button>
              <button
                v-for="type in bookingTypes"
                :key="type"
                class="filter-chip"
                :class="{ active: state.bookingTypeFilter === type }"
                type="button"
                @click="state.bookingTypeFilter = type"
              >
                {{ type }}
              </button>
            </div>
          </section>

          <section class="panel-card">
            <div class="venue-list booking-venue-list">
              <div v-if="state.loadingError" class="empty-state">{{ state.loadingError }}</div>
              <div v-else-if="!filteredBookingVenues.length" class="empty-state">
                {{ state.venues.length ? '没有找到符合条件的场馆。' : '暂无可预约场馆。' }}
              </div>
              <article
                v-for="venue in filteredBookingVenues"
                :key="venue.id"
                class="venue-card interactive-card"
                :class="{ active: venue.id === state.selectedVenueId }"
                @click="openVenueBooking(venue.id)"
              >
                <div class="venue-body">
                  <div class="venue-title">
                    <strong>{{ venue.name }}</strong>
                    <span>{{ venue.resourceCount }} 个场地</span>
                  </div>
                  <p>{{ venue.type }}</p>
                </div>
                <div class="card-link">{{ venue.id === state.selectedVenueId ? '已选择' : '选择场馆' }}</div>
              </article>
            </div>
          </section>
        </section>

        <section v-show="state.activeScreen === 'booking-detail'" class="app-screen active">
          <header class="page-header">
            <div>
              <h2>{{ selectedVenue ? `${selectedVenue.name} 预约` : '完成预约' }}</h2>
            </div>
            <div class="page-header-actions">
              <button class="text-btn icon-btn" type="button" @click="setActiveScreen('booking')">
                <ChevronLeft class="btn-icon btn-icon-sm" />
                <span>返回</span>
              </button>
              <button class="avatar-chip" type="button" aria-label="进入我的页面" @click="openOrdersFromAvatar">
                {{ avatarText }}
              </button>
            </div>
          </header>

          <section class="panel-card">
            <div class="section-head">
              <h3>选择场地</h3>
              <span class="section-note">{{ selectedVenue ? `${selectedVenue.name} · 请选择具体场地` : '请先选择场馆' }}</span>
            </div>
            <div class="resource-grid">
              <div v-if="!selectedVenue" class="empty-state">选择场馆后再选择具体场地。</div>
              <div v-else-if="!selectedVenueResources.length" class="empty-state">当前场馆暂无可预约场地。</div>
              <article
                v-for="resource in selectedVenueResources"
                :key="resource.id"
                class="resource-option"
                :class="{ active: resource.id === state.selectedResourceId }"
                @click="state.selectedResourceId = resource.id; resetSlotSelection(); loadAvailability(true)"
              >
                <strong>{{ selectedVenue.name }} · {{ resource.name }}</strong>
                <p>{{ resource.capacity }} 人 · {{ resource.unitMinutes }} 分钟/段 · ¥{{ resource.price }}</p>
              </article>
            </div>
          </section>

          <section class="panel-card">
            <div class="section-head">
              <h3>预约设置</h3>
              <span class="price">¥{{ selectedResourceBundle?.resource?.price || 0 }}</span>
            </div>
            <div class="form-grid">
              <label class="field-card">
                <span>预约日期</span>
                <input
                  v-model="state.bookingDate"
                  :min="getTodayDateString()"
                  :max="getMaxBookingDateString()"
                  type="date"
                  @change="onBookingDateChange"
                >
              </label>
              <label class="field-card">
                <span>预约人数</span>
                <input v-model="state.bookingSize" type="number" min="1" @input="onBookingSizeChange">
              </label>
            </div>
          </section>

          <section class="panel-card">
            <div class="section-head">
              <h3>选择时段</h3>
              <span class="section-note">支持连续时间段预约</span>
            </div>
            <div class="legend">
              <span><b class="available"></b>可选</span>
              <span><b class="selected"></b>已选</span>
              <span><b class="busy"></b>已占用</span>
            </div>
            <div class="availability-tip">
              <template v-if="!state.selectedVenueId">请先选择场馆。</template>
              <template v-else-if="!state.selectedResourceId">请先选择具体场地。</template>
              <template v-else-if="!state.bookingDate">请先选择预约日期。</template>
              <template v-else-if="!isLoggedIn">请登录后查看可约时段。</template>
              <template v-else-if="!visibleAvailabilitySlots.length">当前日期暂无可用时段数据。</template>
              <template v-else>
                {{ selectedResourceBundle?.resource?.name || '当前场地' }} · {{ state.bookingDate }} · 点击一次选起点，再点一次选终点
                {{ isTodayBookingDate(state.bookingDate) ? ' · 今天已自动过滤当前时刻之前的时段' : '' }}
              </template>
            </div>
            <div class="time-grid">
              <div v-if="state.loadingError" class="empty-state">{{ state.loadingError }}</div>
              <div v-else-if="!state.selectedVenueId" class="empty-state">先选择场馆，再继续选择场地、人数和时段。</div>
              <div v-else-if="!state.selectedResourceId" class="empty-state">选择场地后加载时段。</div>
              <div v-else-if="!state.bookingDate" class="empty-state">选择日期后加载时段。</div>
              <div v-else-if="!isLoggedIn" class="empty-state">请登录后查看</div>
              <div v-else-if="!visibleAvailabilitySlots.length" class="empty-state">暂无可展示时段。</div>
              <button
                v-for="slot in visibleAvailabilitySlots"
                :key="slot.slotUnit"
                class="time-slot"
                :class="{ selected: selectedSlots.includes(slot.slotUnit), busy: !isSlotAvailableForSize(slot) }"
                :disabled="!isSlotAvailableForSize(slot)"
                @click="pickSlotUnit(slot.slotUnit)"
              >
                <span>{{ slot.startTime }}</span>
                <small>{{ getSlotStateText(slot, !isSlotAvailableForSize(slot)) }}</small>
              </button>
            </div>
          </section>

          <section class="panel-card booking-summary-card">
            <div class="section-head">
              <h3>预约信息</h3>
              <span class="price">¥{{ summaryPrice }}</span>
            </div>
            <dl class="detail-list">
              <div><dt>场地</dt><dd>{{ selectedResourceBundle ? `${selectedResourceBundle.venue.name} · ${selectedResourceBundle.resource.name}` : '未选择' }}</dd></div>
              <div><dt>日期</dt><dd>{{ formatDate(state.bookingDate) }}</dd></div>
              <div><dt>时段</dt><dd>{{ formatSlotRange(selectedSlotsSorted, selectedUnitMinutes) }}</dd></div>
              <div><dt>人数</dt><dd>{{ state.bookingSize || 1 }} 人</dd></div>
            </dl>
          </section>
        </section>

        <section v-show="state.activeScreen === 'orders'" class="app-screen active">
          <header class="page-header">
            <div>
              <h2>{{ ordersTitle }}</h2>
            </div>
            <div class="page-header-actions">
              <button v-show="isLoggedIn" class="text-btn icon-btn" type="button" @click="setActiveScreen('settings')">
                <Settings class="btn-icon btn-icon-sm" />
                <span>设置</span>
              </button>
            </div>
          </header>

          <section v-show="isLoggedIn" class="profile-card">
            <div class="profile-main">
              <div class="avatar">{{ avatarText }}</div>
              <div>
                <h3>{{ displayName || '访客用户' }}</h3>
                <p>{{ state.profile ? `用户名：${state.profile.username} · ${getRoleLabel(state.profile.role)}` : '登录后查看预约统计与历史记录' }}</p>
              </div>
            </div>
            <div class="profile-balance">余额 ¥{{ (((state.profile?.balance || 0) / 100)).toFixed(2) }}</div>
            <div class="profile-stats">
              <div><strong>{{ state.profile?.activeReservations || 0 }}</strong><span>待使用</span></div>
              <div><strong>{{ state.profile?.totalReservations || 0 }}</strong><span>总预约</span></div>
              <div><strong>{{ state.profile?.cancelledReservations || 0 }}</strong><span>已取消</span></div>
            </div>
          </section>

          <section v-show="!isLoggedIn" class="login-guide-card">
            <p class="guest-brand-name">CourtFlow</p>
            <div class="avatar guest-avatar">{{ avatarText }}</div>
            <div class="auth-mode-tabs" role="tablist" aria-label="登录或注册">
              <button class="auth-mode-btn" :class="{ active: state.authMode === 'login' }" type="button" @click="setAuthMode('login')">登录</button>
              <button class="auth-mode-btn" :class="{ active: state.authMode === 'register' }" type="button" @click="setAuthMode('register')">注册</button>
            </div>
            <div class="form-grid auth-form-grid">
              <label class="field-card">
                <span>用户名</span>
                <input v-model="state.authForm.username" type="text" maxlength="32" placeholder="请输入用户名">
              </label>
              <label v-show="state.authMode === 'register'" class="field-card">
                <span>用户昵称</span>
                <input v-model="state.authForm.nickname" type="text" maxlength="32" placeholder="请输入用户昵称">
              </label>
              <label class="field-card">
                <span>密码</span>
                <input v-model="state.authForm.password" type="password" maxlength="64" placeholder="请输入密码">
              </label>
              <label v-show="state.authMode === 'register'" class="field-card">
                <span>确认密码</span>
                <input v-model="state.authForm.confirmPassword" type="password" maxlength="64" placeholder="请再次输入密码">
              </label>
            </div>
            <button class="primary-btn login-guide-btn icon-btn" type="button" @click="submitAuthForm">
              <component :is="state.authMode === 'register' ? UserPlus : LogIn" class="btn-icon btn-icon-sm" />
              <span>{{ state.authMode === 'register' ? '注册并登录' : '登录' }}</span>
            </button>
            <p class="settings-message auth-message">{{ state.authMessage }}</p>
            <p class="auth-demo-tip">本地测试账号：caojinshuo / 12345，zhangxiang / 12345</p>
          </section>

          <div v-show="isLoggedIn">
            <div class="filter-bar">
              <button class="filter-chip" :class="{ active: state.ordersFilter === 'all' }" type="button" @click="state.ordersFilter = 'all'">全部</button>
              <button class="filter-chip" :class="{ active: state.ordersFilter === 'active' }" type="button" @click="state.ordersFilter = 'active'">待使用</button>
              <button class="filter-chip" :class="{ active: state.ordersFilter === 'done' }" type="button" @click="state.ordersFilter = 'done'">已完成</button>
              <button class="filter-chip" :class="{ active: state.ordersFilter === 'cancelled' }" type="button" @click="state.ordersFilter = 'cancelled'">已取消</button>
            </div>

            <div class="order-list">
              <div v-if="state.loadingError" class="empty-state">{{ state.loadingError }}</div>
              <div v-else-if="!filteredReservations.length" class="empty-state">当前没有预约记录，去“预约”页面创建一条新的预约即可。</div>
              <article
                v-for="item in filteredReservations"
                :key="item.id"
                class="order-card"
                :class="{ featured: item.status === 0 || item.status === 1 }"
              >
                <div class="order-head">
                  <div>
                    <h3>{{ getResourceMeta(item.resourceId).resourceName || `场地 #${item.resourceId}` }}</h3>
                    <p>{{ formatDate(item.slotDate) }} {{ formatSlotRange(rangeUnits(item.startUnit, item.endUnit), getResourceMeta(item.resourceId).unitMinutes) }}</p>
                  </div>
                  <span class="order-status" :class="reservationStatusMeta(item.status).klass">{{ reservationStatusMeta(item.status).label }}</span>
                </div>
                <div class="order-tags">
                  <span>{{ getResourceMeta(item.resourceId).venueName || `场馆 #${item.venueId}` }}</span>
                  <span>{{ item.size }} 人</span>
                </div>
                <div v-if="item.status === 0 || item.status === 1" class="order-actions">
                  <button class="ghost-btn icon-btn" type="button" @click="cancelReservation(item.id)">
                    <X class="btn-icon btn-icon-sm" />
                    <span>取消预约</span>
                  </button>
                </div>
              </article>
            </div>
          </div>
        </section>

        <section v-show="state.activeScreen === 'settings'" class="app-screen active">
          <header class="page-header">
            <div>
              <h2>账号设置</h2>
            </div>
            <div class="page-header-actions">
              <button class="text-btn icon-btn" type="button" @click="setActiveScreen('orders')">
                <ChevronLeft class="btn-icon btn-icon-sm" />
                <span>返回</span>
              </button>
            </div>
          </header>

          <section class="panel-card">
            <div class="section-head">
              <h3>账号信息</h3>
            </div>
            <div class="form-grid settings-form-grid">
              <label class="field-card">
                <span>用户名</span>
                <input :value="state.profile?.username || ''" type="text" readonly>
              </label>
              <label class="field-card">
                <span>用户昵称</span>
                <input v-model="state.settingsForm.nickname" type="text" maxlength="32" placeholder="请输入用户昵称">
              </label>
            </div>
            <div class="settings-actions">
              <button class="primary-btn icon-btn settings-submit-btn" type="button" @click="saveProfileSettings">
                <Save class="btn-icon btn-icon-sm" />
                <span>保存昵称</span>
              </button>
            </div>
            <p class="settings-message">{{ state.settingsProfileMessage }}</p>
          </section>

          <section class="panel-card">
            <div class="section-head">
              <h3>修改密码</h3>
            </div>
            <div class="form-grid settings-form-grid">
              <label class="field-card">
                <span>当前密码</span>
                <input v-model="state.settingsForm.currentPassword" type="password" maxlength="64" placeholder="请输入当前密码">
              </label>
              <label class="field-card">
                <span>新密码</span>
                <input v-model="state.settingsForm.newPassword" type="password" maxlength="64" placeholder="请输入新密码">
              </label>
              <label class="field-card">
                <span>确认新密码</span>
                <input v-model="state.settingsForm.confirmPassword" type="password" maxlength="64" placeholder="请再次输入新密码">
              </label>
            </div>
            <div class="settings-actions">
              <button class="primary-btn icon-btn settings-submit-btn" type="button" @click="savePasswordSettings">
                <Save class="btn-icon btn-icon-sm" />
                <span>修改密码</span>
              </button>
            </div>
            <p class="settings-message">{{ state.settingsPasswordMessage }}</p>
          </section>

          <section class="settings-logout-section">
            <button class="danger-btn icon-btn settings-logout-btn" type="button" @click="logoutUser">
              <LogOut class="btn-icon btn-icon-sm" />
              <span>退出登录</span>
            </button>
          </section>
        </section>
      </div>

      <div v-show="bookingActionVisible" class="booking-action">
        <div>
          <strong>已选择 {{ selectedSlotsSorted.length }} 个时间段</strong>
          <p>{{ canSubmitBooking ? '预约信息已完整，可直接提交。' : state.bookingStatusMessage }}</p>
        </div>
        <button class="primary-btn icon-btn" :disabled="!canSubmitBooking" type="button" @click="submitBooking">
          <Check class="btn-icon btn-icon-sm" />
          <span>确认预约</span>
        </button>
      </div>

      <nav class="bottom-nav" aria-label="底部导航">
        <button class="tab-btn tab-btn-icon" :class="{ active: isTabActive('home') }" type="button" @click="setActiveScreen('home')">
          <Home class="tab-icon" />
          <span>首页</span>
        </button>
        <button class="tab-btn tab-btn-icon" :class="{ active: isTabActive('booking') }" type="button" @click="setActiveScreen('booking')">
          <CalendarDays class="tab-icon" />
          <span>预约</span>
        </button>
        <button class="tab-btn tab-btn-icon" :class="{ active: isTabActive('orders') }" type="button" @click="setActiveScreen('orders')">
          <User class="tab-icon" />
          <span>我的</span>
        </button>
      </nav>
    </section>
  </main>
</template>
