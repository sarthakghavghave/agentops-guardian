const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const apiBaseUrl = import.meta.env.DEV ? '' : configuredBaseUrl.replace(/\/$/, '')

export const getApiUrl = (path: string) => `${apiBaseUrl}/api${path}`

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(getApiUrl(path), {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const body = await response.text()
    throw new ApiError(
      body || `Request failed with status ${response.status}.`,
      response.status,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T, Body>(path: string, body: Body) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T, Body>(path: string, body: Body) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
}
