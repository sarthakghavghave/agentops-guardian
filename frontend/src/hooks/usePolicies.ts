import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createPolicy,
  getPolicies,
  getPolicy,
  updatePolicy,
} from '../api/policies'
import type { PolicyDefinitionRequest } from '../types/policy'
import { queryKeys } from './queryKeys'

export function usePolicies() {
  return useQuery({
    queryKey: queryKeys.policies.list,
    queryFn: getPolicies,
    retry: false,
  })
}

export function usePolicy(policyId: string) {
  return useQuery({
    queryKey: queryKeys.policies.detail(policyId),
    queryFn: () => getPolicy(policyId),
    enabled: policyId.length > 0,
    retry: false,
  })
}

export function useCreatePolicy() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: PolicyDefinitionRequest) => createPolicy(request),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: queryKeys.policies.list,
      }),
  })
}

export function useUpdatePolicy(policyId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: PolicyDefinitionRequest) => updatePolicy(policyId, request),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.policies.list }),
        queryClient.invalidateQueries({
          queryKey: queryKeys.policies.detail(policyId),
        }),
      ])
    },
  })
}
