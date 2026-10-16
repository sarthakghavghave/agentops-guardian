const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || ''

export function getApiUrl(path: string): string {
  const base = apiBaseUrl.replace(/\/+$/, '')
  return `${base}${path.startsWith('/') ? path : `/${path}`}`
}

export class ApiRequestError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message)
    this.name = 'ApiRequestError'
  }
}

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(getApiUrl(path), {
    ...init,
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const body = await response.text()
    const details = getErrorDetails(body)
    throw new ApiRequestError(
      `Request failed (${response.status} ${response.statusText})${details ? `: ${details}` : ''}`,
      response.status,
    )
  }

  return response.json() as Promise<T>
}

function getErrorDetails(body: string): string {
  if (!body) return ''
  try {
    const parsed: unknown = JSON.parse(body)
    if (typeof parsed === 'object' && parsed !== null) {
      const details = parsed as Record<string, unknown>
      for (const field of ['error', 'message', 'detail', 'title']) {
        if (typeof details[field] === 'string') return details[field]
      }
    }
  } catch {
    return body
  }
  return body
}
