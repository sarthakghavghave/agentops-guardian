import { apiRequest } from './client'
import type {
  GovernanceInterventionResponse,
  InterventionResolutionRequest,
} from '../types/api'

export function listInterventions(): Promise<GovernanceInterventionResponse[]> {
  return apiRequest('/api/interventions')
}

export function getIntervention(
  interventionId: string,
): Promise<GovernanceInterventionResponse> {
  return apiRequest(`/api/interventions/${encodeURIComponent(interventionId)}`)
}

export function approveIntervention(
  interventionId: string,
  request: InterventionResolutionRequest,
): Promise<GovernanceInterventionResponse> {
  return apiRequest(`/api/interventions/${encodeURIComponent(interventionId)}/approve`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}

export function rejectIntervention(
  interventionId: string,
  request: InterventionResolutionRequest,
): Promise<GovernanceInterventionResponse> {
  return apiRequest(`/api/interventions/${encodeURIComponent(interventionId)}/reject`, {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
