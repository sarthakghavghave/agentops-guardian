import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  approveIntervention,
  getIntervention,
  getInterventions,
  rejectIntervention,
} from '../api/interventions'
import { ApiError } from '../api/client'
import type { InterventionResolutionRequest } from '../types/intervention'
import { queryKeys } from './queryKeys'

export function useInterventions() {
  return useQuery({
    queryKey: queryKeys.interventions.list,
    queryFn: getInterventions,
  })
}

export function useIntervention(interventionId: string) {
  return useQuery({
    queryKey: queryKeys.interventions.detail(interventionId),
    queryFn: () => getIntervention(interventionId),
    enabled: interventionId.length > 0,
  })
}

export function useResolveIntervention(interventionId: string) {
  const queryClient = useQueryClient()

  const invalidateInterventions = () =>
    queryClient.invalidateQueries({
      queryKey: queryKeys.interventions.all,
    })

  const approve = useMutation({
    mutationFn: (request: InterventionResolutionRequest) =>
      approveIntervention(interventionId, request),
    onSuccess: invalidateInterventions,
    onError: (error) =>
      error instanceof ApiError && error.status === 409
        ? invalidateInterventions()
        : undefined,
  })

  const reject = useMutation({
    mutationFn: (request: InterventionResolutionRequest) =>
      rejectIntervention(interventionId, request),
    onSuccess: invalidateInterventions,
    onError: (error) =>
      error instanceof ApiError && error.status === 409
        ? invalidateInterventions()
        : undefined,
  })

  return { approve, reject }
}
