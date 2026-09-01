const apiBase = (import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')

export function assetUrl(value) {
  if (!value || /^(https?:|data:|blob:)/i.test(value)) return value
  return apiBase ? `${apiBase}/${String(value).replace(/^\//, '')}` : value
}
