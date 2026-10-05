import { apiClient } from './client'
import type {
  GovernanceInterventionResponse,
  InterventionResolutionRequest,
} from '../types/intervention'

export const getInterventions = () =>
  apiClient.get<GovernanceInterventionResponse[]>('/interventions')

export const getIntervention = (interventionId: string) =>
  apiClient.get<GovernanceInterventionResponse>(
    `/interventions/${encodeURIComponent(interventionId)}`,
  )

export const approveIntervention = (
  interventionId: string,
  request: InterventionResolutionRequest,
) =>
  apiClient.post<GovernanceInterventionResponse, InterventionResolutionRequest>(
    `/interventions/${encodeURIComponent(interventionId)}/approve`,
    request,
  )

export const rejectIntervention = (
  interventionId: string,
  request: InterventionResolutionRequest,
) =>
  apiClient.post<GovernanceInterventionResponse, InterventionResolutionRequest>(
    `/interventions/${encodeURIComponent(interventionId)}/reject`,
    request,
  )
