import { apiClient } from './client'
import type { WorkflowSummaryResponse, WorkflowTrajectoryResponse } from '../types/workflow'

export const getWorkflows = () =>
  apiClient.get<WorkflowSummaryResponse[]>('/workflows')

export const getWorkflow = (workflowId: string) =>
  apiClient.get<WorkflowTrajectoryResponse>(`/workflows/${encodeURIComponent(workflowId)}`)
