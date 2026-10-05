import type { WorkflowCapability } from './workflow'

export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL'

export type PolicyConditionField =
  | 'DATA_CLASSIFICATION'
  | 'TOOL_NAME'
  | 'WORKFLOW_TYPE'
  | 'WORKFLOW_STATE'
  | 'CAPABILITY'

export type PolicyConditionOperator = 'EQUALS' | 'NOT_EQUALS' | 'IN'

export type PolicyAction = 'ALLOW' | 'BLOCK'

export type PolicyDefinition = {
  policyId: string
  name: string
  description: string | null
  enabled: boolean
  priority: number
  targetToolName: string | null
  targetCapability: WorkflowCapability | null
  conditionField: PolicyConditionField
  conditionOperator: PolicyConditionOperator
  conditionValue: string
  action: PolicyAction
}

export type PolicyDefinitionRequest = PolicyDefinition
