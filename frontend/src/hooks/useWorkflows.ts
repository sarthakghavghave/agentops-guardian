import { useQueries, useQuery } from '@tanstack/react-query'
import { getWorkflow, getWorkflows } from '../api/workflows'
import { queryKeys } from './queryKeys'

const trajectoryCacheTime = 30_000

export function useWorkflowSummaries() {
  return useQuery({
    queryKey: queryKeys.workflows.summaries,
    queryFn: getWorkflows,
  })
}

export function useWorkflowTrajectory(workflowId: string) {
  return useQuery({
    queryKey: queryKeys.workflows.trajectory(workflowId),
    queryFn: () => getWorkflow(workflowId),
    enabled: workflowId.length > 0,
    staleTime: trajectoryCacheTime,
  })
}

export function useWorkflowTrajectories(workflowIds: string[]) {
  return useQueries({
    queries: workflowIds.map((workflowId) => ({
      queryKey: queryKeys.workflows.trajectory(workflowId),
      queryFn: () => getWorkflow(workflowId),
      enabled: workflowId.length > 0,
      staleTime: trajectoryCacheTime,
    })),
  })
}
