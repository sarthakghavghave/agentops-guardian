import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiRequestError, apiRequest } from '../../api/client'
import {
  createCoordinatorRequest,
  isUnavailableConversationError,
} from './chatSession'

describe('createCoordinatorRequest', () => {
  it('omits the conversation ID when starting a conversation', () => {
    expect(createCoordinatorRequest('Hello', null)).toEqual({ message: 'Hello' })
  })

  it('reuses the current conversation ID on a follow-up', () => {
    expect(createCoordinatorRequest('Follow-up', 'conversation-123')).toEqual({
      message: 'Follow-up',
      conversationId: 'conversation-123',
    })
  })
})

describe('isUnavailableConversationError', () => {
  it.each([404, 410])('recognizes HTTP %s as an unavailable conversation', (status) => {
    expect(isUnavailableConversationError(Object.assign(new Error('Request failed'), { status })))
      .toBe(true)
  })

  it('recognizes the backend invalid conversation ID response', () => {
    expect(
      isUnavailableConversationError(
        Object.assign(new Error('Request failed (400 Bad Request): Conversation id is invalid.'), {
          status: 400,
        }),
      ),
    ).toBe(true)
  })

  it('does not classify unrelated bad requests as an unavailable conversation', () => {
    expect(
      isUnavailableConversationError(
        Object.assign(new Error('Request failed (400 Bad Request): Message is required.'), {
          status: 400,
        }),
      ),
    ).toBe(false)
  })
})

describe('apiRequest coordinator error handling', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('preserves the backend status and expired-session detail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: 'Conversation has expired. Start a new conversation.' }), {
          status: 410,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    await expect(apiRequest('/api/agent-coordinator/run')).rejects.toMatchObject({
      name: 'ApiRequestError',
      status: 410,
      message: expect.stringContaining('Conversation has expired'),
    } satisfies Partial<ApiRequestError>)
  })
})
