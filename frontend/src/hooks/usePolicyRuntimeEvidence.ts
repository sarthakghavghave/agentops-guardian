import { useMemo } from 'react'
import type { WorkflowTrajectoryEvent } from '../types/audit'
import { useWorkflowSummaries, useWorkflowTrajectories } from './useWorkflows'

export type PolicyRuntimeEvidenceItem = {
  workflowId: string
  event: WorkflowTrajectoryEvent
}

export function usePolicyRuntimeEvidence(policyId: string) {
  const workflowsQuery = useWorkflowSummaries()
  const workflowIds = useMemo(
    () => (workflowsQuery.data ?? []).map((workflow) => workflow.workflowId),
    [workflowsQuery.data],
  )
  const trajectoryQueries = useWorkflowTrajectories(workflowIds)

  const evidence = useMemo<PolicyRuntimeEvidenceItem[]>(() => {
    if (!workflowsQuery.data) return []

    return trajectoryQueries.flatMap((trajectoryQuery) => {
      const workflow = trajectoryQuery.data
      if (!workflow) return []

      return workflow.events
        .filter(
          (event) =>
            typeof event.policyId === 'string' &&
            event.policyId.trim().length > 0 &&
            event.policyId === policyId &&
            typeof event.policyDecision === 'string' &&
            event.policyDecision.trim().length > 0,
        )
        .map((event) => ({ workflowId: workflow.workflowId, event }))
    })
  }, [policyId, trajectoryQueries, workflowsQuery.data])

  const hasTrajectoryError = trajectoryQueries.some((query) => query.isError)
  const isLoading =
    workflowsQuery.isPending ||
    (workflowsQuery.isSuccess &&
      trajectoryQueries.some((query) => query.isPending || query.isFetching))

  return {
    evidence,
    isLoading,
    isError: workflowsQuery.isError || hasTrajectoryError,
  }
}
