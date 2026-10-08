import { useState, type FormEvent } from 'react'
import type { WorkflowCapability } from '../../types/workflow'
import type {
  PolicyAction,
  PolicyConditionField,
  PolicyConditionOperator,
  PolicyDefinition,
  PolicyDefinitionRequest,
} from '../../types/policy'
import { Panel } from '../../components/ui/Panel'
import { Link } from 'react-router-dom'
import { PolicyStatusBadge } from './PolicyStatusBadge'
import { policyConditionPhrase } from './PolicyListShell'

const capabilities: WorkflowCapability[] = [
  'READ_CUSTOMER_DATA',
  'READ_ORDER',
  'READ_PRODUCT',
  'GENERATE_REPORT',
  'SEND_EMAIL',
  'RETURN_ORDER',
]

const conditionFields: PolicyConditionField[] = [
  'DATA_CLASSIFICATION',
  'TOOL_NAME',
  'WORKFLOW_TYPE',
  'WORKFLOW_STATE',
  'CAPABILITY',
]

const conditionOperators: PolicyConditionOperator[] = ['EQUALS', 'NOT_EQUALS', 'IN']
const actions: PolicyAction[] = ['ALLOW', 'BLOCK']

function selectValue<T extends string>(value: string, options: readonly T[]): T | '' {
  return options.find((option) => option === value) ?? ''
}

type PolicyFormValues = {
  policyId: string
  name: string
  description: string
  enabled: boolean
  priority: string
  targetToolName: string
  targetCapability: WorkflowCapability | ''
  conditionField: PolicyConditionField | ''
  conditionOperator: PolicyConditionOperator | ''
  conditionValue: string
  action: PolicyAction | ''
}

type PolicyFormProps = {
  policy?: PolicyDefinition
  isSubmitting: boolean
  serverError: string | null
  onSubmit: (request: PolicyDefinitionRequest) => Promise<void>
}

function initialValues(policy?: PolicyDefinition): PolicyFormValues {
  if (!policy) {
    return {
      policyId: '',
      name: '',
      description: '',
      enabled: false,
      priority: '',
      targetToolName: '',
      targetCapability: '',
      conditionField: '',
      conditionOperator: '',
      conditionValue: '',
      action: '',
    }
  }

  return {
    policyId: policy.policyId,
    name: policy.name,
    description: policy.description ?? '',
    enabled: policy.enabled,
    priority: String(policy.priority),
    targetToolName: policy.targetToolName ?? '',
    targetCapability: policy.targetCapability ?? '',
    conditionField: policy.conditionField,
    conditionOperator: policy.conditionOperator,
    conditionValue: policy.conditionValue,
    action: policy.action,
  }
}

function makeRequest(
  values: PolicyFormValues & {
    targetCapability: WorkflowCapability | ''
    conditionField: PolicyConditionField
    conditionOperator: PolicyConditionOperator
    action: PolicyAction
  },
  priority: number,
): PolicyDefinitionRequest {
  return {
    policyId: values.policyId,
    name: values.name,
    description: values.description,
    enabled: values.enabled,
    priority,
    targetToolName: values.targetToolName.trim() ? values.targetToolName : null,
    targetCapability: values.targetCapability || null,
    conditionField: values.conditionField as PolicyConditionField,
    conditionOperator: values.conditionOperator as PolicyConditionOperator,
    conditionValue: values.conditionValue,
    action: values.action as PolicyAction,
  }
}

export function PolicyForm({ policy, isSubmitting, serverError, onSubmit }: PolicyFormProps) {
  const editing = policy !== undefined
  const [values, setValues] = useState(() => initialValues(policy))
  const [validationMessage, setValidationMessage] = useState<string | null>(null)
  const [priorityError, setPriorityError] = useState<string | null>(null)
  const toolNameTarget = values.targetToolName.trim()
  const capabilityTarget = values.targetCapability
  const targetPreview =
    [toolNameTarget, capabilityTarget].filter(Boolean).join(' / ') || 'a selected target'
  const conditionPreview =
    values.conditionField && values.conditionOperator
      ? `${values.conditionField} ${policyConditionPhrase(values.conditionOperator)} ${values.conditionValue || '[condition value]'}`
      : 'the selected condition'
  const actionPreview = values.action || 'the selected action'

  function update<K extends keyof PolicyFormValues>(field: K, value: PolicyFormValues[K]) {
    setValues((current) => ({ ...current, [field]: value }))
    setValidationMessage(null)
    setPriorityError(null)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setValidationMessage(null)
    setPriorityError(null)

    if (!values.targetToolName.trim() && !values.targetCapability) {
      setValidationMessage('Select a target capability or enter a target tool name.')
      return
    }

    const priorityNumber = Number(values.priority)
    if (
      values.priority.trim() === '' ||
      !Number.isInteger(priorityNumber) ||
      priorityNumber < -2147483648 ||
      priorityNumber > 2147483647
    ) {
      setPriorityError('Enter a valid integer priority.')
      return
    }

    if (!values.policyId.trim() || !values.name.trim()) {
      setValidationMessage('Enter both a policy ID and a name.')
      return
    }

    if (!values.conditionField || !values.conditionOperator || !values.action) {
      setValidationMessage('Choose a condition field, condition operator, and action.')
      return
    }

    void onSubmit(makeRequest({
      ...values,
      conditionField: values.conditionField,
      conditionOperator: values.conditionOperator,
      action: values.action,
    }, priorityNumber))
  }

  return (
    <form className="space-y-5" onSubmit={handleSubmit} noValidate>
      {editing && (
        <div className="rounded-md border border-blue-200 bg-blue-50 p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-blue-950">Editing an existing enforcement rule</p>
              <p className="mt-1 break-all font-mono text-xs text-blue-800">{policy.policyId}</p>
            </div>
            <PolicyStatusBadge enabled={values.enabled} />
          </div>
          <p className="mt-2 text-xs leading-5 text-blue-900">
            Changes are not applied until you explicitly save this policy.
          </p>
        </div>
      )}

      {validationMessage && (
        <p className="rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" role="alert">
          {validationMessage}
        </p>
      )}
      {serverError && (
        <p className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-800" role="alert">
          {serverError}
        </p>
      )}

      <Panel title="Policy identity" description="Identify the enterprise-defined governance control.">
        <div className="grid gap-4 p-4 sm:grid-cols-2 sm:p-5">
          <label className="block min-w-0 text-sm font-medium text-slate-700" htmlFor="policy-id">
            Policy ID
            <input
              autoComplete="off"
              className="mt-1.5 block w-full min-w-0 rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20 read-only:bg-slate-100"
              id="policy-id"
              onChange={(event) => update('policyId', event.target.value)}
              readOnly={editing}
              required
              value={values.policyId}
            />
            {editing && <span className="mt-1 block text-xs text-muted">Policy IDs cannot be changed.</span>}
          </label>
          <label className="block min-w-0 text-sm font-medium text-slate-700" htmlFor="policy-name">
            Name
            <input
              className="mt-1.5 block w-full min-w-0 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="policy-name"
              onChange={(event) => update('name', event.target.value)}
              required
              value={values.name}
            />
          </label>
          <label className="block text-sm font-medium text-slate-700 sm:col-span-2" htmlFor="policy-description">
            Description
            <textarea
              className="mt-1.5 block min-h-24 w-full resize-y rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="policy-description"
              onChange={(event) => update('description', event.target.value)}
              value={values.description}
            />
          </label>
        </div>
      </Panel>

      <Panel title="Enforcement" description="Set whether this rule is currently enabled and its configured priority.">
        <div className="grid gap-4 p-4 sm:grid-cols-2 sm:p-5">
          <label className="flex min-h-12 items-center gap-3 rounded-md border border-line p-3">
            <input
              checked={values.enabled}
              className="h-4 w-4 accent-accent"
              onChange={(event) => update('enabled', event.target.checked)}
              type="checkbox"
            />
            <span>
              <span className="block text-sm font-medium text-slate-800">
                {values.enabled ? 'Enabled' : 'Disabled'}
              </span>
              <span className="block text-xs text-muted">
                {values.enabled ? 'This policy is marked as enabled.' : 'This policy is not currently enforcing.'}
              </span>
            </span>
          </label>
          <label className="block text-sm font-medium text-slate-700" htmlFor="policy-priority">
            Priority
            <input
              aria-describedby={priorityError ? 'priority-error' : undefined}
              className="mt-1.5 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="policy-priority"
              inputMode="numeric"
              onChange={(event) => update('priority', event.target.value)}
              step="1"
              type="number"
              value={values.priority}
            />
            {priorityError && <span className="mt-1 block text-xs text-red-700" id="priority-error">{priorityError}</span>}
          </label>
        </div>
      </Panel>

      <Panel title="Target" description="At least one target capability or tool name is required. Both may be specified.">
        <div className="grid gap-4 p-4 sm:grid-cols-2 sm:p-5">
          <label className="block text-sm font-medium text-slate-700" htmlFor="target-capability">
            Target capability
            <select
              className="mt-1.5 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="target-capability"
              onChange={(event) => update('targetCapability', selectValue(event.target.value, capabilities))}
              value={values.targetCapability}
            >
              <option value="">No capability selected</option>
              {capabilities.map((capability) => <option key={capability} value={capability}>{capability}</option>)}
            </select>
          </label>
          <label className="block text-sm font-medium text-slate-700" htmlFor="target-tool">
            Target tool name
            <input
              className="mt-1.5 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="target-tool"
              onChange={(event) => update('targetToolName', event.target.value)}
              value={values.targetToolName}
            />
          </label>
        </div>
      </Panel>

      <Panel title="Condition" description="Choose the backend-supported field and operator, then supply its value.">
        <div className="grid gap-4 p-4 sm:grid-cols-3 sm:p-5">
          <label className="block text-sm font-medium text-slate-700" htmlFor="condition-field">
            Condition field
            <select
              className="mt-1.5 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="condition-field"
              onChange={(event) => update('conditionField', selectValue(event.target.value, conditionFields))}
              required
              value={values.conditionField}
            >
              <option value="">Select a field</option>
              {conditionFields.map((field) => <option key={field} value={field}>{field}</option>)}
            </select>
          </label>
          <label className="block text-sm font-medium text-slate-700" htmlFor="condition-operator">
            Condition operator
            <select
              className="mt-1.5 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="condition-operator"
              onChange={(event) => update('conditionOperator', selectValue(event.target.value, conditionOperators))}
              required
              value={values.conditionOperator}
            >
              <option value="">Select an operator</option>
              {conditionOperators.map((operator) => <option key={operator} value={operator}>{operator}</option>)}
            </select>
          </label>
          <label className="block text-sm font-medium text-slate-700" htmlFor="condition-value">
            Condition value
            <input
              className="mt-1.5 block w-full min-w-0 rounded-md border border-slate-300 bg-white px-3 py-2 font-mono text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="condition-value"
              onChange={(event) => update('conditionValue', event.target.value)}
              value={values.conditionValue}
            />
          </label>
        </div>
      </Panel>

      <Panel title="Action" description="Select the action this policy defines when its condition matches.">
        <div className="p-4 sm:p-5">
          <label className="block max-w-md text-sm font-medium text-slate-700" htmlFor="policy-action">
            Policy action
            <select
              className="mt-1.5 block w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-ink-900 outline-none focus:border-accent focus:ring-2 focus:ring-accent/20"
              id="policy-action"
              onChange={(event) => update('action', selectValue(event.target.value, actions))}
              required
              value={values.action}
            >
              <option value="">Select an action</option>
              {actions.map((action) => <option key={action} value={action}>{action}</option>)}
            </select>
          </label>
        </div>
      </Panel>

      <Panel title="Rule preview" description="This summarizes the current form values; it does not simulate policy enforcement.">
        <p className="break-words p-4 text-sm leading-6 text-slate-700 sm:p-5">
          When enabled, this policy targets{' '}
          <span className="font-mono font-semibold">{targetPreview}</span> when{' '}
          <span className="font-mono font-semibold">{conditionPreview}</span> and applies{' '}
          <span className="font-mono font-semibold">{actionPreview}</span>.
          {!values.enabled && <span className="ml-1 text-muted">It is currently disabled.</span>}
        </p>
      </Panel>

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Link
          aria-disabled={isSubmitting}
          className={`inline-flex min-h-10 items-center justify-center rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 ${
            isSubmitting ? 'pointer-events-none opacity-60' : ''
          }`}
          to={editing ? `/policies/${encodeURIComponent(policy.policyId)}` : '/policies'}
        >
          Cancel
        </Link>
        <button
          className="min-h-10 rounded-md bg-accent px-4 py-2 text-sm font-semibold text-white hover:bg-accent/90 disabled:cursor-not-allowed disabled:opacity-60"
          disabled={isSubmitting}
          type="submit"
        >
          {isSubmitting ? 'Saving policy…' : editing ? 'Save policy' : 'Create policy'}
        </button>
      </div>
    </form>
  )
}
