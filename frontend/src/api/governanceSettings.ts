import { apiClient } from './client'

export type GovernanceSettings = {
  enabled: boolean
}

export const getGovernanceSettings = () =>
  apiClient.get<GovernanceSettings>('/governance/settings')

export const updateGovernanceEnabled = (enabled: boolean) =>
  apiClient.put<GovernanceSettings, GovernanceSettings>(
    '/governance/settings',
    { enabled },
  )
