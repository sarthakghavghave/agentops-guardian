// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { GovernanceActivityPanel } from './GovernanceActivityPanel'
import type { GovernanceEventStreamPayload } from '../../types/api'

afterEach(cleanup)

function blockedHistoryEvent(): GovernanceEventStreamPayload {
  return {
    eventId: 17,
    conversationId: 'conversation-1',
    workflowId: 'workflow-1',
    agentName: 'EmailAgent',
    workflowType: 'EMAIL_COMMUNICATION',
    event: {
      timestamp: '2026-10-17T03:00:00Z',
      eventType: 'POLICY_DECISION',
      toolCallId: 'call-1',
      toolName: 'readConversationHistory',
      capability: 'READ_CONVERSATION_HISTORY',
      sequence: 2,
      policyDecision: 'BLOCK',
      policyId: 'HISTORY_ACCESS_POLICY',
      policyReason: 'Conversation history access is not permitted for this request.',
      executionStatus: 'BLOCKED',
      errorType: null,
      errorMessage: null,
      nodeBeforeId: null,
      nodeBeforeState: null,
      nodeBeforeCapability: null,
      nodeAfterId: null,
      nodeAfterState: null,
      nodeAfterCapability: null,
      stateBefore: null,
      stateAfter: null,
      classificationBefore: null,
      classificationAfter: null,
      transformationType: null,
      riskLevel: 'HIGH',
      intervention: 'BLOCK',
      riskReason: null,
      interventionId: null,
      interventionStatus: null,
      resolvedBy: null,
      resolutionReason: null,
    },
  }
}

describe('GovernanceActivityPanel', () => {
  it('presents a blocked conversation-history decision as a dismissible alert with its reason', () => {
    render(
      <GovernanceActivityPanel
        conversationId="conversation-1"
        events={[blockedHistoryEvent()]}
        status="LIVE"
      />,
    )

    expect(screen.getByRole('alert').textContent).toContain('Governance blocked this action')
    expect(screen.getByRole('alert').textContent).toContain('readConversationHistory')
    expect(screen.getByRole('alert').textContent)
      .toContain('Conversation history access is not permitted for this request.')

    fireEvent.click(screen.getByRole('button', { name: 'Dismiss governance alert' }))
    expect(screen.queryByRole('alert')).toBeNull()
  })
})
