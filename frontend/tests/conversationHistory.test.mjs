import assert from 'node:assert/strict'
import test from 'node:test'
import { groupWorkflowsByConversation, workflowTypeLabel } from '../src/features/dashboard/conversationHistory.ts'
import {
  appendUniqueLiveGovernanceEvent,
  eventsForWorkflow,
  mergePersistedAndLiveEvents,
} from '../src/hooks/liveGovernanceState.ts'
import { parseLiveGovernanceEvent } from '../src/types/liveGovernanceEvent.ts'
import {
  groupTrajectoryEvents,
  trajectoryGroupLabel,
} from '../src/features/dashboard/workflowPresentation.ts'

function workflow(workflowId, conversationId, startedAt) {
  return {
    workflowId,
    conversationId,
    agentName: 'Agent',
    workflowType: 'CUSTOMER_REPORTING',
    startedAt,
    lastEventAt: startedAt,
    currentNodeId: null,
    currentState: null,
    eventCount: 0,
  }
}

test('conversation selection groups only matching non-null conversations and sorts workflows by start time', () => {
  const summaries = [
    workflow('workflow-later', 'conversation-a', '2026-10-16T10:02:00Z'),
    workflow('standalone', null, '2026-10-16T10:00:00Z'),
    workflow('workflow-earlier', 'conversation-a', '2026-10-16T10:01:00Z'),
    workflow('workflow-other', 'conversation-b', '2026-10-16T09:00:00Z'),
    workflow('workflow-earlier', 'conversation-a', '2026-10-16T10:01:00Z'),
  ]

  const groups = groupWorkflowsByConversation(summaries)

  assert.deepEqual(groups.map(({ conversationId }) => conversationId), ['conversation-a', 'conversation-b'])
  assert.deepEqual(
    groups[0].workflows.map(({ workflowId }) => workflowId),
    ['workflow-earlier', 'workflow-later'],
  )
  assert.equal(groups.flatMap(({ workflows }) => workflows).some(({ workflowId }) => workflowId === 'standalone'), false)
})

test('EmailAgent governance events parse and receive an accurate workflow label', () => {
  const event = {
    timestamp: '2026-10-16T10:00:00Z',
    eventType: 'EXECUTION_OUTCOME',
    toolCallId: 'email-call',
    toolName: 'sendEmail',
    capability: 'SEND_EMAIL',
    sequence: 1,
    policyDecision: 'ALLOW',
    policyId: null,
    policyReason: null,
    executionStatus: 'SUCCESS',
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
  }

  const parsed = parseLiveGovernanceEvent(JSON.stringify({
    eventId: 42,
    conversationId: 'conversation-a',
    workflowId: 'email-workflow',
    agentName: 'EmailAgent',
    workflowType: 'EMAIL_COMMUNICATION',
    event,
  }))

  assert.equal(parsed?.workflowType, 'EMAIL_COMMUNICATION')
  assert.equal(parsed?.conversationId, 'conversation-a')
  assert.equal(parsed?.agentName, 'EmailAgent')
  assert.equal(workflowTypeLabel(parsed.workflowType), 'Email Communication')
})

test('Read Conversation History capability is accepted in live governance events', () => {
  const event = liveEvent(43, 'conversation-a', 'history-workflow', '2026-10-16T10:00:00Z', {
    eventType: 'PROPOSED_ACTION',
    toolCallId: 'history-call',
    toolName: 'readConversationHistory',
    capability: 'READ_CONVERSATION_HISTORY',
    policyDecision: null,
    executionStatus: null,
  })

  const parsed = parseLiveGovernanceEvent(JSON.stringify(event))

  assert.equal(parsed?.event.capability, 'READ_CONVERSATION_HISTORY')
  assert.equal(parsed?.event.toolName, 'readConversationHistory')
})

function liveEvent(eventId, conversationId, workflowId, timestamp, overrides = {}) {
  const event = {
    timestamp,
    eventType: 'EXECUTION_OUTCOME',
    toolCallId: `call-${eventId}`,
    toolName: 'sendEmail',
    capability: 'SEND_EMAIL',
    sequence: eventId,
    policyDecision: 'ALLOW',
    policyId: null,
    policyReason: null,
    executionStatus: 'SUCCESS',
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
    ...overrides,
  }
  return {
    eventId,
    conversationId,
    workflowId,
    agentName: 'EmailAgent',
    workflowType: 'EMAIL_COMMUNICATION',
    event,
  }
}

test('live events route only to their conversation and workflow', () => {
  const firstConversationEvent = liveEvent(1, 'conversation-a', 'workflow-a', '2026-10-16T10:00:00Z')
  const otherConversationEvent = liveEvent(2, 'conversation-b', 'workflow-b', '2026-10-16T10:01:00Z')
  const otherWorkflowEvent = liveEvent(3, 'conversation-a', 'workflow-c', '2026-10-16T10:02:00Z')

  assert.deepEqual(
    eventsForWorkflow(
      [firstConversationEvent, otherConversationEvent, otherWorkflowEvent],
      'conversation-a',
      'workflow-a',
    ),
    [firstConversationEvent],
  )
})

test('early live events create one temporary workflow entry until the persisted summary arrives', () => {
  const first = liveEvent(8, 'conversation-a', 'workflow-pending', '2026-10-16T10:01:00Z')
  const next = liveEvent(9, 'conversation-a', 'workflow-pending', '2026-10-16T10:02:00Z')

  const beforeSummary = groupWorkflowsByConversation([], [first, next])
  assert.equal(beforeSummary.length, 1)
  assert.equal(beforeSummary[0].workflows.length, 1)
  assert.equal(beforeSummary[0].workflows[0].workflowId, 'workflow-pending')
  assert.equal(beforeSummary[0].workflows[0].eventCount, 2)
  assert.equal(beforeSummary[0].workflows[0].workflowType, 'EMAIL_COMMUNICATION')

  const persistedSummary = workflow('workflow-pending', 'conversation-a', '2026-10-16T10:00:00Z')
  const afterSummary = groupWorkflowsByConversation([persistedSummary], [first, next])
  assert.equal(afterSummary[0].workflows.length, 1)
  assert.equal(afterSummary[0].workflows[0].startedAt, persistedSummary.startedAt)
})

test('REST events reconcile identical live payloads without dropping distinct matching events', () => {
  const persisted = liveEvent(10, 'conversation-a', 'workflow-a', '2026-10-16T10:00:00Z')
  const samePersistedEvent = structuredClone(persisted.event)
  const distinctLive = liveEvent(11, 'conversation-a', 'workflow-a', '2026-10-16T10:01:00Z')

  const reconciled = mergePersistedAndLiveEvents(
    [persisted.event],
    [{ ...persisted, event: samePersistedEvent }, distinctLive],
  )

  assert.deepEqual(
    reconciled.map(({ timestamp }) => timestamp),
    ['2026-10-16T10:00:00Z', '2026-10-16T10:01:00Z'],
  )
})

test('trajectory grouping keeps internal history events, governance details, order, and duplicate suppression', () => {
  const historyProposed = liveEvent(50, 'conversation-a', 'email-workflow', '2026-10-16T10:00:00Z', {
    eventType: 'PROPOSED_ACTION',
    toolCallId: 'history-call',
    toolName: 'readConversationHistory',
    capability: 'READ_CONVERSATION_HISTORY',
    policyDecision: null,
    executionStatus: null,
  })
  const historyPolicy = liveEvent(51, 'conversation-a', 'email-workflow', '2026-10-16T10:00:01Z', {
    eventType: 'POLICY_DECISION',
    toolCallId: 'history-call',
    toolName: 'readConversationHistory',
    capability: 'READ_CONVERSATION_HISTORY',
    executionStatus: null,
  })
  const historyOutcome = liveEvent(52, 'conversation-a', 'email-workflow', '2026-10-16T10:00:02Z', {
    eventType: 'EXECUTION_OUTCOME',
    toolCallId: 'history-call',
    toolName: 'readConversationHistory',
    capability: 'READ_CONVERSATION_HISTORY',
  })
  const emailProposed = liveEvent(53, 'conversation-a', 'email-workflow', '2026-10-16T10:00:03Z', {
    eventType: 'PROPOSED_ACTION',
    toolCallId: 'email-call',
    toolName: 'sendEmail',
    policyDecision: null,
    executionStatus: null,
  })
  const emailOutcome = liveEvent(54, 'conversation-a', 'email-workflow', '2026-10-16T10:00:04Z', {
    eventType: 'EXECUTION_OUTCOME',
    toolCallId: 'email-call',
    toolName: 'sendEmail',
  })
  const persistedEvents = [
    historyProposed,
    historyPolicy,
    historyOutcome,
    emailProposed,
    emailOutcome,
  ].map(({ event }) => event)
  const reconciled = mergePersistedAndLiveEvents(persistedEvents, [
    { ...historyProposed, event: structuredClone(historyProposed.event) },
    { ...historyOutcome, event: structuredClone(historyOutcome.event) },
    { ...emailOutcome, event: structuredClone(emailOutcome.event) },
  ])
  const groups = groupTrajectoryEvents(reconciled)

  assert.equal(reconciled.length, persistedEvents.length)
  assert.deepEqual(groups.map(trajectoryGroupLabel), ['Read Conversation History', 'Send Email'])
  assert.equal(groups[0].events.length, 3)
  assert.equal(groups[0].events[1].policyDecision, 'ALLOW')
  assert.equal(groups[0].events[2].executionStatus, 'SUCCESS')
  assert.equal(groups[1].events.length, 2)
})

test('replayed SSE IDs are ignored while new persisted replay IDs are accepted after reconnect', () => {
  const delivered = liveEvent(21, 'conversation-a', 'workflow-a', '2026-10-16T10:00:00Z')
  const replayed = structuredClone(delivered)
  const missedDuringDisconnect = liveEvent(22, 'conversation-a', 'workflow-a', '2026-10-16T10:01:00Z')

  const afterReconnect = [delivered].reduce(
    (events, event) => appendUniqueLiveGovernanceEvent(events, event, 100),
    [],
  )
  const afterReplay = [replayed, missedDuringDisconnect].reduce(
    (events, event) => appendUniqueLiveGovernanceEvent(events, event, 100),
    afterReconnect,
  )

  assert.deepEqual(afterReplay.map(({ eventId }) => eventId), [21, 22])
})
