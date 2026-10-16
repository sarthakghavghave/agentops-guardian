const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const base = apiBaseUrl.replace(/\/+$/, '')
  const response = await fetch(`${base}${path}`, {
    ...init,
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const details = await response.text()
    throw new Error(
      `Request failed (${response.status} ${response.statusText})${details ? `: ${details}` : ''}`,
    )
  }

  return response.json() as Promise<T>
}
