import type { AgentCoordinatorRequest } from '../../types/api'

export function createCoordinatorRequest(
  message: string,
  conversationId: string | null,
): AgentCoordinatorRequest {
  return conversationId ? { message, conversationId } : { message }
}

export function isUnavailableConversationError(error: unknown): boolean {
  if (!(error instanceof Error)) return false

  const status = 'status' in error ? error.status : undefined
  if (status === 404 || status === 410) return true
  return status === 400 && /conversation id is invalid/i.test(error.message)
}
