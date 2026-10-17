import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Check, LoaderCircle, RefreshCw, X } from 'lucide-react'
import {
  approveIntervention,
  listInterventions,
  rejectIntervention,
} from '../../api/interventions'
import { getWorkflowTrajectory } from '../../api/workflows'
import type {
  GovernanceInterventionResponse,
  WorkflowTrajectoryResponse,
} from '../../types/api'
import {
  createResolutionRequest,
  pendingInterventions,
  resolutionOutcome,
} from './interventionReviewState'
import type { ResolutionAction } from './interventionReviewState'

type ResolutionForm = {
  interventionId: string
  action: ResolutionAction
}

type ResolutionResult = {
  action: ResolutionAction
  intervention: GovernanceInterventionResponse
  trajectory: WorkflowTrajectoryResponse | null
  trajectoryError: string | null
}

type InterventionReviewProps = {
  conversationId: string | null
}

export function InterventionReview({ conversationId }: InterventionReviewProps) {
  const [interventions, setInterventions] = useState<GovernanceInterventionResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isRefreshing, setIsRefreshing] = useState(false)
  const [activeResolutionId, setActiveResolutionId] = useState<string | null>(null)
  const [resolutionForm, setResolutionForm] = useState<ResolutionForm | null>(null)
  const [resolvedBy, setResolvedBy] = useState('')
  const [resolutionReason, setResolutionReason] = useState('')
  const [listError, setListError] = useState<string | null>(null)
  const [resolutionError, setResolutionError] = useState<string | null>(null)
  const [result, setResult] = useState<ResolutionResult | null>(null)

  const refresh = useCallback(async () => {
    setIsRefreshing(true)
    setListError(null)
    try {
      setInterventions(await listInterventions())
    } catch (error) {
      setListError(errorMessage(error))
    } finally {
      setIsLoading(false)
      setIsRefreshing(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    setResolutionForm(null)
    setResolutionError(null)
    setResult(null)
  }, [conversationId])

  function openResolution(interventionId: string, action: ResolutionAction) {
    setResolutionForm({ interventionId, action })
    setResolvedBy('')
    setResolutionReason('')
    setResolutionError(null)
  }

  function closeResolution() {
    if (activeResolutionId !== null) return
    setResolutionForm(null)
    setResolutionError(null)
  }

  async function handleResolutionSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!resolutionForm || activeResolutionId !== null) return
    const request = createResolutionRequest(resolvedBy, resolutionReason)
    if (!request) {
      setResolutionError('Enter both your name and a resolution reason.')
      return
    }

    const { interventionId, action } = resolutionForm
    setActiveResolutionId(interventionId)
    setResolutionError(null)
    setResult(null)

    try {
      const resolved = action === 'approve'
        ? await approveIntervention(interventionId, request)
        : await rejectIntervention(interventionId, request)
      setResolutionForm(null)
      setResult({
        action,
        intervention: resolved,
        trajectory: null,
        trajectoryError: null,
      })

      const [trajectoryResult] = await Promise.allSettled([
        getWorkflowTrajectory(resolved.workflowId),
        refresh(),
      ])
      if (trajectoryResult.status === 'fulfilled') {
        setResult((current) => current
          ? { ...current, trajectory: trajectoryResult.value }
          : current)
      } else {
        setResult((current) => current
          ? { ...current, trajectoryError: errorMessage(trajectoryResult.reason) }
          : current)
      }
    } catch (error) {
      setResolutionError(errorMessage(error))
      await refresh()
    } finally {
      setActiveResolutionId(null)
    }
  }

  const pending = pendingInterventions(interventions, conversationId)
  const selectedIntervention = resolutionForm
    ? pending.find((intervention) => intervention.interventionId === resolutionForm.interventionId)
    : undefined

  return (
    <section aria-labelledby="intervention-review-heading" className="intervention-review">
      <header className="intervention-review-header">
        <div className="intervention-review-title">
          <h2 id="intervention-review-heading">Pending interventions</h2>
          {!isLoading && <span className="pending-count">{pending.length}</span>}
        </div>
        <button
          aria-label="Refresh pending interventions"
          className="intervention-refresh"
          disabled={isRefreshing || activeResolutionId !== null}
          onClick={() => void refresh()}
          type="button"
        >
          {isRefreshing
            ? <LoaderCircle aria-hidden="true" className="spin" size={13} />
            : <RefreshCw aria-hidden="true" size={13} />}
          Refresh
        </button>
      </header>

      {isLoading ? (
        <p className="intervention-state" role="status">
          <LoaderCircle aria-hidden="true" className="spin" size={13} />
          Loading pending interventions…
        </p>
      ) : listError ? (
        <div className="intervention-error" role="alert">
          <span>Could not load interventions. {listError}</span>
          <button disabled={isRefreshing} onClick={() => void refresh()} type="button">Try again</button>
        </div>
      ) : !conversationId ? (
        <p className="intervention-state">Start a conversation to see its pending interventions.</p>
      ) : pending.length === 0 ? (
        <p className="intervention-state">No pending interventions.</p>
      ) : (
        <div aria-label="Pending intervention list" className="intervention-list">
          {pending.map((intervention) => (
            <article className="intervention-card" key={intervention.interventionId}>
              <div className="intervention-card-main">
                <div className="intervention-action">
                  <strong>{intervention.toolName || intervention.capability}</strong>
                  <span className="intervention-status">PENDING</span>
                </div>
                <p className="intervention-context">
                  {intervention.agentName} · {intervention.workflowType.replace(/_/g, ' ')}
                  {` · ${intervention.riskLevel} risk`}
                </p>
                <p className="intervention-reason">{intervention.reason}</p>
                <p className="intervention-ids">
                  Workflow {intervention.workflowId}
                  {intervention.conversationId ? ` · Conversation ${intervention.conversationId}` : ''}
                </p>
              </div>
              {resolutionForm?.interventionId === intervention.interventionId ? (
                <form className="resolution-form" onSubmit={handleResolutionSubmit}>
                  <label>
                    Resolved by
                    <input
                      autoComplete="name"
                      disabled={activeResolutionId !== null}
                      onChange={(change) => setResolvedBy(change.target.value)}
                      required
                      value={resolvedBy}
                    />
                  </label>
                  <label>
                    Resolution reason
                    <textarea
                      disabled={activeResolutionId !== null}
                      onChange={(change) => setResolutionReason(change.target.value)}
                      required
                      rows={2}
                      value={resolutionReason}
                    />
                  </label>
                  {resolutionError && <p className="resolution-error" role="alert">{resolutionError}</p>}
                  <div className="resolution-form-actions">
                    <button
                      className={`resolution-submit resolution-submit-${resolutionForm.action}`}
                      disabled={activeResolutionId !== null || !resolvedBy.trim() || !resolutionReason.trim()}
                      type="submit"
                    >
                      {activeResolutionId !== null
                        ? <LoaderCircle aria-hidden="true" className="spin" size={13} />
                        : resolutionForm.action === 'approve'
                          ? <Check aria-hidden="true" size={13} />
                          : <X aria-hidden="true" size={13} />}
                      {activeResolutionId !== null
                        ? 'Submitting…'
                        : resolutionForm.action === 'approve' ? 'Confirm approval' : 'Confirm rejection'}
                    </button>
                    <button
                      className="resolution-cancel"
                      disabled={activeResolutionId !== null}
                      onClick={closeResolution}
                      type="button"
                    >
                      Cancel
                    </button>
                  </div>
                </form>
              ) : (
                <div className="intervention-actions">
                  <button
                    className="intervention-approve"
                    disabled={activeResolutionId !== null}
                    onClick={() => openResolution(intervention.interventionId, 'approve')}
                    type="button"
                  >
                    Approve
                  </button>
                  <button
                    className="intervention-reject"
                    disabled={activeResolutionId !== null}
                    onClick={() => openResolution(intervention.interventionId, 'reject')}
                    type="button"
                  >
                    Reject
                  </button>
                </div>
              )}
            </article>
          ))}
        </div>
      )}

      {selectedIntervention && activeResolutionId === null && !resolutionError && (
        <p className="resolution-guidance">
          {resolutionForm?.action === 'approve'
            ? 'Approval permits resumption; it does not guarantee successful execution.'
            : 'Rejecting terminates the pending task.'}
        </p>
      )}

      {result?.intervention.conversationId === conversationId && (
        <div
          className={`resolution-result${result.intervention.status === 'APPROVED' || result.intervention.status === 'REJECTED' ? ' resolution-result-confirmed' : ' resolution-result-warning'}`}
          role="status"
        >
          <strong className="resolution-result-heading">
            {result.action === 'approve' ? 'Approval response' : 'Rejection response'}
          </strong>
          <div className="resolution-outcome-badges">
            <span className={`resolution-decision resolution-decision-${result.intervention.status.toLowerCase()}`}>
              Governance · {result.intervention.status}
            </span>
            <span className={`resolution-task resolution-task-${(result.intervention.taskStatus || 'unknown').toLowerCase()}`}>
              Task · {result.intervention.taskStatus || 'Outcome unavailable'}
            </span>
          </div>
          <p>{resolutionOutcome(result.intervention, result.action)}</p>
          {result.trajectory && (
            <p className="workflow-refresh-result">
              Refreshed workflow: {result.trajectory.eventCount} persisted events
              {result.trajectory.currentState ? ` · ${result.trajectory.currentState}` : ''}
            </p>
          )}
          {result.trajectoryError && (
            <p className="resolution-error">
              Resolution was returned by the backend, but workflow refresh failed: {result.trajectoryError}
            </p>
          )}
        </div>
      )}
    </section>
  )
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'An unexpected error occurred.'
}
