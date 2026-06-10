export function formatMinutes(totalMinutes) {
  const hour = String(Math.floor(totalMinutes / 60)).padStart(2, '0')
  const minute = String(totalMinutes % 60).padStart(2, '0')
  return `${hour}:${minute}`
}

export function toMinutes(value) {
  const [hour, minute] = String(value || '00:00').split(':').map(Number)
  return hour * 60 + minute
}

export function formatDate(input, withWeekday = true) {
  if (!input) {
    return '未选择'
  }

  const date = new Date(input)
  if (Number.isNaN(date.getTime())) {
    return String(input)
  }

  return date.toLocaleDateString('zh-CN', withWeekday
    ? { month: '2-digit', day: '2-digit', weekday: 'short' }
    : { month: '2-digit', day: '2-digit' })
}

export function formatDateTime(input) {
  if (!input) {
    return '--'
  }

  const date = new Date(input)
  if (Number.isNaN(date.getTime())) {
    return String(input)
  }

  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatSlotRange(slotUnits, unitMinutes = 10) {
  if (!slotUnits?.length) {
    return '未选择'
  }

  const sorted = [...slotUnits].sort((left, right) => left - right)
  const start = formatMinutes(sorted[0] * unitMinutes)
  const end = formatMinutes((sorted[sorted.length - 1] + 1) * unitMinutes)
  return `${start} - ${end}`
}

export function formatInputDate(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function rangeUnits(start, end) {
  const result = []
  for (let unit = start; unit <= end; unit += 1) {
    result.push(unit)
  }
  return result
}
