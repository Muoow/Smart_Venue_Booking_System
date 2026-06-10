export async function apiRequest(path, options = {}, token = '') {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  }

  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(path, {
    ...options,
    headers,
  })

  const data = await response.json().catch(() => ({
    code: response.status,
    message: '接口返回内容无法解析',
  }))

  if (!response.ok || data.code !== 200) {
    const error = new Error(data.message || '请求失败')
    error.status = response.status
    error.code = data.code
    throw error
  }

  return data.data
}
