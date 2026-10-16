import { describe, expect, it } from 'vitest'
import {
  getLatestGovernanceEvent,
  getVisibleGovernanceEvents,
  groupEventsByWorkflow,
  mergeUniqueGovernanceEvents,
  parseGovernanceEvent,
  selectConversationEvents,
} from './governanceActivity'
import type { GovernanceEventStreamPayload } from '../../types/api'

function payload(
  eventId: number,
  conversationId: string | null,
  workflowId: string,
  agentName = 'EmailAgent',
): GovernanceEventStreamPayload {
  return {
    eventId,
    conversationId,
    workflowId,
    agentName,
    workflowType: 'EMAIL_COMMUNICATION',
    event: {
      timestamp: `2026-10-16T10:00:${String(eventId).padStart(2, '0')}Z`,
      eventType: 'POLICY_DECISION',
      toolCallId: `tool-call-${eventId}`,
      toolName: 'sendEmail',
      capability: 'SEND_EMAIL',
      sequence: eventId,
      policyDecision: 'ALLOW',
      policyId: 'EMAIL_POLICY',
      policyReason: null,
      executionStatus: null,
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
      riskLevel: null,
      intervention: null,
      riskReason: null,
      interventionId: null,
      interventionStatus: null,
      resolvedBy: null,
      resolutionReason: null,
    },
  }
}

describe('governance activity routing', () => {
  it('shows only the latest event when collapsed and refreshes it as events arrive', () => {
    const older = payload(5, 'conversation-a', 'workflow-a')
    const latest = payload(8, 'conversation-a', 'workflow-a')

    expect(getLatestGovernanceEvent([older])).toEqual(older)
    expect(getVisibleGovernanceEvents([older], false)).toEqual([older])
    expect(getVisibleGovernanceEvents([older, latest], false)).toEqual([latest])
  })

  it('shows every event in event-ID order when expanded', () => {
    const earliest = payload(3, 'conversation-a', 'workflow-a')
    const latest = payload(9, 'conversation-a', 'workflow-a')
    const middle = payload(6, 'conversation-a', 'workflow-a')

    expect(getVisibleGovernanceEvents([latest, earliest, middle], true))
      .toEqual([earliest, middle, latest])
  })

  it('renders all retained events across workflow groups in expanded mode', () => {
    const firstWorkflowEvent = payload(1, 'conversation-a', 'workflow-a')
    const secondWorkflowEvent = payload(2, 'conversation-a', 'workflow-b', 'CustomerSupportAgent')
    const visibleGroups = groupEventsByWorkflow(
      getVisibleGovernanceEvents([firstWorkflowEvent, secondWorkflowEvent], true),
    )

    expect(visibleGroups).toHaveLength(2)
    expect(visibleGroups.flatMap((workflow) => workflow.events))
      .toEqual([firstWorkflowEvent, secondWorkflowEvent])
  })

  it('returns an empty collapsed view when the conversation has no events', () => {
    expect(getVisibleGovernanceEvents([], false)).toEqual([])
    expect(getLatestGovernanceEvent([])).toBeNull()
  })

  it('keeps only events with an exact active conversation ID', () => {
    const matching = payload(1, 'conversation-a', 'workflow-a')
    const unrelated = payload(2, 'conversation-b', 'workflow-b')
    const missingConversation = payload(3, null, 'workflow-c')

    expect(
      selectConversationEvents(
        [matching, unrelated, missingConversation],
        'conversation-a',
      ),
    ).toEqual([matching])
  })

  it('does not attribute buffered events before a conversation ID is known', () => {
    expect(selectConversationEvents([payload(1, 'conversation-a', 'workflow-a')], null))
      .toEqual([])
  })

  it('groups events by their actual workflow IDs and retains workflow attribution', () => {
    const first = payload(1, 'conversation-a', 'workflow-a')
    const second = payload(2, 'conversation-a', 'workflow-a')
    const anotherWorkflow = payload(3, 'conversation-a', 'workflow-b', 'CustomerSupportAgent')

    expect(groupEventsByWorkflow([first, second, anotherWorkflow])).toEqual([
      {
        workflowId: 'workflow-a',
        conversationId: 'conversation-a',
        agentName: 'EmailAgent',
        workflowType: 'EMAIL_COMMUNICATION',
        events: [first, second],
      },
      {
        workflowId: 'workflow-b',
        conversationId: 'conversation-a',
        agentName: 'CustomerSupportAgent',
        workflowType: 'EMAIL_COMMUNICATION',
        events: [anotherWorkflow],
      },
    ])
  })

  it('deduplicates stable IDs and retains out-of-order lower IDs in backend order', () => {
    const higher = payload(12, 'conversation-a', 'workflow-a')
    const lower = payload(11, 'conversation-a', 'workflow-a')

    expect(mergeUniqueGovernanceEvents([higher], [lower, higher])).toEqual([lower, higher])
  })

  it('switches the visible activity to the newly selected conversation', () => {
    const firstConversationEvent = payload(1, 'conversation-a', 'workflow-a')
    const secondConversationEvent = payload(2, 'conversation-b', 'workflow-b')
    const events = [firstConversationEvent, secondConversationEvent]

    expect(selectConversationEvents(events, 'conversation-a')).toEqual([firstConversationEvent])
    expect(selectConversationEvents(events, 'conversation-b')).toEqual([secondConversationEvent])
  })

  it('parses the existing event stream payload and rejects malformed payloads', () => {
    const event = payload(7, 'conversation-a', 'workflow-a')

    expect(parseGovernanceEvent(JSON.stringify(event))).toEqual(event)
    expect(parseGovernanceEvent('{')).toBeNull()
    expect(parseGovernanceEvent(JSON.stringify({ ...event, eventId: '7' }))).toBeNull()
  })
})
