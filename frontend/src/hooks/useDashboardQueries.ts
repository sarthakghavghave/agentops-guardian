import { useInterventions } from './useInterventions'
import { useWorkflowSummaries } from './useWorkflows'

export function useDashboardQueries() {
  const workflows = useWorkflowSummaries()
  const interventions = useInterventions()

  return { workflows, interventions }
}
