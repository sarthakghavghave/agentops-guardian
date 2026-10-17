import { apiRequest } from './client'
import type { AgentCoordinatorRequest, AgentCoordinatorResponse } from '../types/api'

export function runAgentCoordinator(
  request: AgentCoordinatorRequest,
): Promise<AgentCoordinatorResponse> {
  return apiRequest('/api/agent-coordinator/run', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
