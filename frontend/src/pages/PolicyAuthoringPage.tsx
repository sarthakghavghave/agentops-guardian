import { ArrowLeft } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { EmptyState } from '../components/feedback/EmptyState'
import { ErrorState } from '../components/feedback/ErrorState'
import { LoadingState } from '../components/feedback/LoadingState'
import { PolicyForm } from '../features/policies/PolicyForm'
import { useCreatePolicy, usePolicy, useUpdatePolicy } from '../hooks/usePolicies'
import type { PolicyDefinitionRequest } from '../types/policy'

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    try {
      const parsed: unknown = JSON.parse(error.message)
      if (
        typeof parsed === 'object' &&
        parsed !== null &&
        'message' in parsed &&
        typeof parsed.message === 'string'
      ) {
        return parsed.message
      }
    } catch {
      return error.message || 'The policy could not be saved.'
    }
    return error.message || 'The policy could not be saved.'
  }

  return 'The policy could not be saved. Please try again.'
}

export function PolicyAuthoringPage() {
  const { policyId } = useParams<{ policyId: string }>()
  const editing = policyId !== undefined
  const navigate = useNavigate()
  const policyQuery = usePolicy(policyId ?? '')
  const createMutation = useCreatePolicy()
  const updateMutation = useUpdatePolicy(policyId ?? '')
  const isSubmitting = createMutation.isPending || updateMutation.isPending
  const mutationError = createMutation.error ?? updateMutation.error

  async function savePolicy(request: PolicyDefinitionRequest) {
    createMutation.reset()
    updateMutation.reset()

    try {
      const saved = editing
        ? await updateMutation.mutateAsync(request)
        : await createMutation.mutateAsync(request)
      navigate(`/policies/${encodeURIComponent(saved.policyId)}`, { replace: true })
    } catch {
      // Mutation errors are rendered from TanStack Query state; form values remain mounted.
    }
  }

  if (editing && policyQuery.isPending) {
    return <LoadingState label="Loading policy for editing" />
  }

  if (editing && policyQuery.isError && !policyQuery.data) {
    const notFound = policyQuery.error instanceof ApiError && policyQuery.error.status === 404
    return (
      <div className="space-y-5">
        <PolicyListLink />
        {notFound ? (
          <EmptyState
            title="Policy not found"
            description="This policy record could not be found, so it cannot be edited."
          />
        ) : (
          <ErrorState message="The policy could not be loaded for editing. Please try again later." />
        )}
      </div>
    )
  }

  if (editing && !policyQuery.data) {
    return (
      <div className="space-y-5">
        <PolicyListLink />
        <ErrorState message="The policy response was unavailable, so editing cannot continue." />
      </div>
    )
  }

  return (
    <div className="space-y-5">
      <PolicyListLink />
      <header>
        <h2 className="text-xl font-semibold tracking-tight text-ink-950">
          {editing ? 'Edit policy' : 'Create policy'}
        </h2>
        <p className="mt-1 text-sm text-muted">
          {editing
            ? 'Update the enterprise-defined enforcement rule. Changes are saved only when you submit.'
            : 'Define an enterprise governance rule for Guardian to evaluate against agent actions.'}
        </p>
      </header>
      {policyQuery.isError && policyQuery.data && (
        <ErrorState message="The latest policy refresh failed. The previously loaded policy values are shown." />
      )}
      <PolicyForm
        key={editing ? policyQuery.data?.policyId : 'create'}
        isSubmitting={isSubmitting}
        onSubmit={savePolicy}
        policy={editing ? policyQuery.data : undefined}
        serverError={mutationError ? errorMessage(mutationError) : null}
      />
    </div>
  )
}

function PolicyListLink() {
  return (
    <Link
      className="inline-flex items-center gap-2 text-sm font-medium text-slate-600 hover:text-accent"
      to="/policies"
    >
      <ArrowLeft aria-hidden="true" size={16} />
      Back to policies
    </Link>
  )
}
