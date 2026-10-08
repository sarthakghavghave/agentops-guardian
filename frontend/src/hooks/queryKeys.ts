export const queryKeys = {
  workflows: {
    summaries: ['workflows', 'summaries'] as const,
    trajectory: (workflowId: string) => ['workflows', 'trajectory', workflowId] as const,
  },
  interventions: {
    all: ['interventions'] as const,
    list: ['interventions', 'list'] as const,
    detail: (interventionId: string) => ['interventions', 'detail', interventionId] as const,
  },
  policies: {
    all: ['policies'] as const,
    list: ['policies', 'list'] as const,
    detail: (policyId: string) => ['policies', 'detail', policyId] as const,
  },
}
