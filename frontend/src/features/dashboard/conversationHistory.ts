import type { WorkflowSummaryResponse, WorkflowType } from '../../types/workflow'

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
): ConversationWorkflowGroup[] {
  const workflowsByConversation = new Map<string, Map<string, WorkflowSummaryResponse>>()

  for (const workflow of summaries) {
    if (!workflow.conversationId) continue
    let workflows = workflowsByConversation.get(workflow.conversationId)
    if (!workflows) {
      workflows = new Map()
      workflowsByConversation.set(workflow.conversationId, workflows)
    }
    workflows.set(workflow.workflowId, workflow)
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
