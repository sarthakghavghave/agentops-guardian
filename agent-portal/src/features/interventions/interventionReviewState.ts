import type {
  GovernanceInterventionResponse,
  InterventionResolutionRequest,
} from '../../types/api'

export type ResolutionAction = 'approve' | 'reject'

export function pendingInterventions(
  interventions: GovernanceInterventionResponse[],
  conversationId: string | null,
): GovernanceInterventionResponse[] {
  if (!conversationId) return []
  return interventions.filter(
    (intervention) =>
      intervention.status === 'PENDING' && intervention.conversationId === conversationId,
  )
}

export function createResolutionRequest(
  resolvedBy: string,
  resolutionReason: string,
): InterventionResolutionRequest | null {
  const actor = resolvedBy.trim()
  const reason = resolutionReason.trim()
  return actor && reason ? { resolvedBy: actor, resolutionReason: reason } : null
}

export function resolutionOutcome(
  intervention: GovernanceInterventionResponse,
  action: ResolutionAction,
): string {
  if (action === 'reject') {
    const status = intervention.status === 'REJECTED'
      ? `Intervention status: ${intervention.status}.`
      : `Backend reported intervention status: ${intervention.status}.`
    const task = intervention.taskStatus
      ? ` Task status: ${intervention.taskStatus}.`
      : ''
    return `${status}${task}${intervention.taskMessage ? ` ${intervention.taskMessage}` : ''}`
  }

  const status = intervention.status === 'APPROVED'
    ? `Intervention status: ${intervention.status}.`
    : `Backend reported intervention status: ${intervention.status}.`
  if (!intervention.taskStatus) {
    return `${status} The response did not include a task-resumption outcome.`
  }
  return `${status} Task status: ${intervention.taskStatus}.${intervention.taskMessage ? ` ${intervention.taskMessage}` : ''}`
}
