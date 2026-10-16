import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { ArrowUpRight, Bot, LoaderCircle, MessageSquarePlus, Send, Sparkles } from 'lucide-react'
import { runAgentCoordinator } from './api/agentCoordinator'
import { ApiRequestError } from './api/client'
import {
  createCoordinatorRequest,
  isUnavailableConversationError,
} from './features/chat/chatSession'
import type { AgentCoordinatorResponse } from './types/api'

const guardianUrl = import.meta.env.VITE_GUARDIAN_URL || 'http://localhost:5173'

type ChatMessage = {
  id: number
  role: 'user' | 'assistant'
  text: string
  response?: AgentCoordinatorResponse
  failed?: boolean
}

export function App() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [failedMessageId, setFailedMessageId] = useState<number | null>(null)
  const nextMessageId = useRef(0)
  const messageListRef = useRef<HTMLDivElement>(null)
  const composerRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    messageListRef.current?.scrollTo({
      top: messageListRef.current.scrollHeight,
      behavior: 'smooth',
    })
  }, [messages, isLoading, error])

  async function sendMessage(messageId: number, text: string) {
    if (isLoading) return

    setIsLoading(true)
    setError(null)
    setFailedMessageId(null)

    try {
      const response = await runAgentCoordinator(
        createCoordinatorRequest(text, conversationId),
      )
      if (!response.conversationId) {
        throw new Error('The coordinator response did not include a conversation ID.')
      }

      setConversationId(response.conversationId)
      setMessages((current) => [
        ...current,
        {
          id: nextMessageId.current++,
          role: 'assistant',
          text: response.response,
          response,
        },
      ])
    } catch (requestError) {
      setMessages((current) =>
        current.map((message) =>
          message.id === messageId ? { ...message, failed: true } : message,
        ),
      )
      setFailedMessageId(messageId)
      setError(requestError)
    } finally {
      setIsLoading(false)
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const text = draft.trim()
    if (!text || isLoading || failedMessageId !== null) return

    const messageId = nextMessageId.current++
    setMessages((current) => [...current, { id: messageId, role: 'user', text }])
    setDraft('')
    void sendMessage(messageId, text)
  }

  function handleComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  function startNewConversation() {
    if (isLoading) return
    setMessages([])
    setConversationId(null)
    setDraft('')
    setError(null)
    setFailedMessageId(null)
    composerRef.current?.focus()
  }

  function retryFailedMessage() {
    if (failedMessageId === null || isLoading) return
    const failedMessage = messages.find(
      (message) => message.id === failedMessageId && message.role === 'user',
    )
    if (failedMessage) void sendMessage(failedMessage.id, failedMessage.text)
  }

  const sessionUnavailable = isUnavailableConversationError(error)
  const failedMessage = messages.find((message) => message.id === failedMessageId)

  return (
    <main className="portal-shell">
      <header className="topbar">
        <a aria-label="Agent Portal home" className="brand" href="/">
          <span className="brand-mark"><Sparkles aria-hidden="true" size={19} /></span>
          <span>
            <strong>AgentOps</strong>
            <small>Agent Portal</small>
          </span>
        </a>
        <div className="topbar-actions">
          <button
            className="new-conversation-button"
            disabled={isLoading}
            onClick={startNewConversation}
            type="button"
          >
            <MessageSquarePlus aria-hidden="true" size={15} />
            New conversation
          </button>
          <a className="guardian-link" href={guardianUrl}>
            Open Guardian
            <ArrowUpRight aria-hidden="true" size={15} />
          </a>
        </div>
      </header>

      <section aria-label="Agent conversation" className="chat-workspace">
        <div className="chat-heading">
          <span className="chat-avatar"><Bot aria-hidden="true" size={18} /></span>
          <div>
            <h1>Agent workspace</h1>
            <p>
              {conversationId
                ? `Continuing conversation · ${conversationId.slice(0, 8)}`
                : 'Ask a question or describe a task'}
            </p>
          </div>
        </div>

        <div
          aria-live="polite"
          aria-relevant="additions text"
          className={`message-list${messages.length === 0 ? ' message-list-empty' : ''}`}
          ref={messageListRef}
          role="log"
        >
          {messages.length === 0 ? (
            <div className="empty-chat">
              <span className="empty-chat-icon"><Sparkles aria-hidden="true" size={20} /></span>
              <h2>How can I help?</h2>
              <p>
                Ask a question or describe what you need. Your conversation stays in
                this browser session.
              </p>
            </div>
          ) : (
            messages.map((message) => (
              <article
                aria-label={message.role === 'user' ? 'Your message' : 'Agent response'}
                className={`message-row message-${message.role}`}
                key={message.id}
              >
                {message.role === 'assistant' && (
                  <span className="message-avatar"><Bot aria-hidden="true" size={16} /></span>
                )}
                <div className="message-content">
                  <div className="message-bubble">{message.text}</div>
                  {message.failed && <span className="failed-label">Response not received</span>}
                  {message.response && (
                    <div className="response-details">
                      <span className="action-tag">{message.response.action.replace(/_/g, ' ')}</span>
                      {message.response.clarificationRequired && (
                        <span className="response-note">Clarification needed</span>
                      )}
                      {message.response.approvalRequired && (
                        <span className="response-note">
                          Approval required
                          {message.response.interventionId
                            ? ` · Intervention ${message.response.interventionId}`
                            : ''}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              </article>
            ))
          )}
          {isLoading && (
            <div aria-label="Waiting for the coordinator" className="loading-row" role="status">
              <span className="message-avatar"><Bot aria-hidden="true" size={16} /></span>
              <span className="loading-bubble">
                <LoaderCircle aria-hidden="true" className="spin" size={15} />
                Waiting for the coordinator…
              </span>
            </div>
          )}
          {error !== null && (
            <div className="error-card" role="alert">
              <div>
                <strong>
                  {sessionUnavailable ? 'This conversation is no longer available' : 'Could not get a response'}
                </strong>
                <p>
                  {sessionUnavailable
                    ? 'Your messages are still shown here, but this conversation ID was rejected. Start a new conversation to continue.'
                    : `${errorMessage(error)} Your message is kept above. No automatic retry was made.`}
                </p>
                {!sessionUnavailable && (
                  <p className="retry-caution">
                    If the request reached the coordinator before the connection failed, retrying may run it again.
                  </p>
                )}
              </div>
              {sessionUnavailable ? (
                <button className="error-action" onClick={startNewConversation} type="button">
                  Start new conversation
                </button>
              ) : (
                <button className="error-action" onClick={retryFailedMessage} type="button">
                  Retry message
                </button>
              )}
            </div>
          )}
          {failedMessage && <span className="sr-only">Failed message: {failedMessage.text}</span>}
        </div>

        <form className="composer" onSubmit={handleSubmit}>
          <label className="sr-only" htmlFor="message-composer">Message the agent</label>
          <textarea
            autoComplete="off"
            disabled={isLoading || failedMessageId !== null}
            id="message-composer"
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={handleComposerKeyDown}
            placeholder={
              failedMessageId !== null
                ? 'Retry the failed message or start a new conversation'
                : 'Write a message…'
            }
            ref={composerRef}
            rows={1}
            value={draft}
          />
          <button
            aria-label="Send message"
            className="send-button"
            disabled={!draft.trim() || isLoading || failedMessageId !== null}
            type="submit"
          >
            {isLoading
              ? <LoaderCircle aria-hidden="true" className="spin" size={17} />
              : <Send aria-hidden="true" size={17} />}
          </button>
          <span className="composer-hint">Enter to send · Shift+Enter for a new line</span>
        </form>
      </section>

      <footer className="footer">
        <span>AgentOps Guardian</span>
        <span>Conversation history is kept in memory for this session only</span>
      </footer>
    </main>
  )
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message
  if (error instanceof Error) return error.message
  return 'An unexpected error occurred.'
}
