import { useQuery } from '@tanstack/react-query'
import { getWorkflow, getWorkflows } from '../api/workflows'
import { queryKeys } from './queryKeys'

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
  })
}
