import assert from 'node:assert/strict'
import test from 'node:test'
import { groupWorkflowsByConversation, workflowTypeLabel } from '../src/features/dashboard/conversationHistory.ts'
import { parseLiveGovernanceEvent } from '../src/types/liveGovernanceEvent.ts'

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
    workflowId: 'email-workflow',
    agentName: 'EmailAgent',
    workflowType: 'EMAIL_COMMUNICATION',
    event,
  }))

  assert.equal(parsed?.workflowType, 'EMAIL_COMMUNICATION')
  assert.equal(parsed?.agentName, 'EmailAgent')
  assert.equal(workflowTypeLabel(parsed.workflowType), 'Email Communication')
})
