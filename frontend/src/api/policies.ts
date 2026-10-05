import { apiClient } from './client'
import type { PolicyDefinition, PolicyDefinitionRequest } from '../types/policy'

export const getPolicies = () => apiClient.get<PolicyDefinition[]>('/policies')

export const getPolicy = (policyId: string) =>
  apiClient.get<PolicyDefinition>(`/policies/${encodeURIComponent(policyId)}`)

export const createPolicy = (request: PolicyDefinitionRequest) =>
  apiClient.post<PolicyDefinition, PolicyDefinitionRequest>('/policies', request)

export const updatePolicy = (policyId: string, request: PolicyDefinitionRequest) =>
  apiClient.put<PolicyDefinition, PolicyDefinitionRequest>(
    `/policies/${encodeURIComponent(policyId)}`,
    request,
  )
