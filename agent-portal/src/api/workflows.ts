import { apiRequest } from './client'
import type { WorkflowSummaryResponse, WorkflowTrajectoryResponse } from '../types/api'

export function listWorkflows(): Promise<WorkflowSummaryResponse[]> {
  return apiRequest('/api/workflows')
}

export function getWorkflowTrajectory(
  workflowId: string,
): Promise<WorkflowTrajectoryResponse> {
  return apiRequest(`/api/workflows/${encodeURIComponent(workflowId)}`)
}
