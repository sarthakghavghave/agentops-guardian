import type { WorkflowSummaryResponse, WorkflowType } from '../../types/workflow'
import type { LiveGovernanceEvent } from '../../types/liveGovernanceEvent'

export type ConversationWorkflowGroup = {
  conversationId: string
  workflows: WorkflowSummaryResponse[]
}

function compareStartTime(left: WorkflowSummaryResponse, right: WorkflowSummaryResponse): number {
  const leftTime = Date.parse(left.startedAt)
  const rightTime = Date.parse(right.startedAt)
  if (Number.isFinite(leftTime) && Number.isFinite(rightTime) && leftTime !== rightTime) {
    return leftTime - rightTime
  }
  if (Number.isFinite(leftTime) !== Number.isFinite(rightTime)) {
    return Number.isFinite(leftTime) ? -1 : 1
  }
  return left.workflowId.localeCompare(right.workflowId)
}

export function groupWorkflowsByConversation(
  summaries: WorkflowSummaryResponse[],
  liveEvents: LiveGovernanceEvent[] = [],
): ConversationWorkflowGroup[] {
  const workflowsByConversation = new Map<string, Map<string, WorkflowSummaryResponse>>()
  const summariesByWorkflowId = new Map(summaries.map((workflow) => [workflow.workflowId, workflow]))
  const liveByWorkflow = new Map<string, LiveGovernanceEvent[]>()
  const conversationByLiveWorkflow = new Map<string, string>()

  for (const workflow of summaries) {
    if (!workflow.conversationId) continue
    let workflows = workflowsByConversation.get(workflow.conversationId)
    if (!workflows) {
      workflows = new Map()
      workflowsByConversation.set(workflow.conversationId, workflows)
    }
    workflows.set(workflow.workflowId, workflow)
  }

  for (const event of liveEvents) {
    if (!event.conversationId) continue
    const persistedSummary = summariesByWorkflowId.get(event.workflowId)
    if (persistedSummary) {
      if (persistedSummary.conversationId !== event.conversationId) continue
    } else {
      const existingConversationId = conversationByLiveWorkflow.get(event.workflowId)
      if (existingConversationId && existingConversationId !== event.conversationId) continue
      conversationByLiveWorkflow.set(event.workflowId, event.conversationId)
      const workflowEvents = liveByWorkflow.get(event.workflowId) ?? []
      if (!workflowEvents.some((existing) => existing.eventId === event.eventId)) {
        workflowEvents.push(event)
        liveByWorkflow.set(event.workflowId, workflowEvents)
      }
    }
  }

  for (const [workflowId, events] of liveByWorkflow) {
    const firstEvent = events.reduce((earliest, current) =>
      Date.parse(current.event.timestamp) < Date.parse(earliest.event.timestamp) ? current : earliest,
    )
    const lastEvent = events.reduce((latest, current) =>
      Date.parse(current.event.timestamp) >= Date.parse(latest.event.timestamp) ? current : latest,
    )
    const conversationId = firstEvent.conversationId
    if (!conversationId) continue
    const workflows = workflowsByConversation.get(conversationId) ?? new Map()
    workflows.set(workflowId, {
      workflowId,
      conversationId,
      agentName: firstEvent.agentName,
      workflowType: firstEvent.workflowType,
      startedAt: firstEvent.event.timestamp,
      lastEventAt: lastEvent.event.timestamp,
      currentNodeId: null,
      currentState: lastEvent.event.stateAfter ?? lastEvent.event.stateBefore,
      eventCount: events.length,
    })
    workflowsByConversation.set(conversationId, workflows)
  }

  return [...workflowsByConversation]
    .map(([conversationId, workflows]) => ({
      conversationId,
      workflows: [...workflows.values()].sort(compareStartTime),
    }))
    .sort((left, right) => {
      const leftLatestActivity = Math.max(...left.workflows.map((workflow) => Date.parse(workflow.lastEventAt)))
      const rightLatestActivity = Math.max(...right.workflows.map((workflow) => Date.parse(workflow.lastEventAt)))
      if (Number.isFinite(leftLatestActivity) && Number.isFinite(rightLatestActivity)
        && leftLatestActivity !== rightLatestActivity) {
        return rightLatestActivity - leftLatestActivity
      }
      return left.conversationId.localeCompare(right.conversationId)
    })
}

export function workflowTypeLabel(workflowType: WorkflowType): string {
  switch (workflowType) {
    case 'CUSTOMER_SUPPORT':
      return 'Customer Support'
    case 'CUSTOMER_REPORTING':
      return 'Customer Reporting'
    case 'EMAIL_COMMUNICATION':
      return 'Email Communication'
  }
}
