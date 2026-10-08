import { useEffect, useRef, useState } from 'react'
import { ArrowRight, ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../../components/feedback/EmptyState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { useWorkflowTrajectory } from '../../hooks/useWorkflows'
import { WorkflowActionCard } from './WorkflowActionCard'
import {
  formatGovernanceLabel,
  getWorkflowGovernanceSummary,
  groupTrajectoryEvents,
} from './workflowPresentation'
import type { WorkflowSummaryResponse } from '../../types/workflow'

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Time unavailable'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function WorkflowTrajectoryRail({
  workflowId,
  fallbackSummary,
}: {
  workflowId: string
  fallbackSummary?: WorkflowSummaryResponse
}) {
  const trajectory = useWorkflowTrajectory(workflowId)
  const railRef = useRef<HTMLDivElement>(null)
  const isNearRightEdge = useRef(true)
  const previousEventCount = useRef(0)
  const [showNewActivity, setShowNewActivity] = useState(false)
  const [expandedGroupKey, setExpandedGroupKey] = useState<string | null>(null)
  const groups = groupTrajectoryEvents(trajectory.data?.events ?? [])
  const eventCount = trajectory.data?.events.length ?? 0
  const governanceSummary = trajectory.data
    ? getWorkflowGovernanceSummary(trajectory.data)
    : null
  const workflow = trajectory.data ?? fallbackSummary

  useEffect(() => {
    if (eventCount <= previousEventCount.current) {
      previousEventCount.current = eventCount
      return
    }

    previousEventCount.current = eventCount
    const rail = railRef.current
    if (!rail) return

    if (isNearRightEdge.current) {
      rail.scrollTo({ left: rail.scrollWidth, behavior: 'smooth' })
      setShowNewActivity(false)
    } else {
      setShowNewActivity(true)
    }
  }, [eventCount])

  const handleScroll = () => {
    const rail = railRef.current
    if (!rail) return

    isNearRightEdge.current =
      rail.scrollWidth - rail.scrollLeft - rail.clientWidth < 80
    if (isNearRightEdge.current) setShowNewActivity(false)
  }

  const scrollToLatest = () => {
    const rail = railRef.current
    if (!rail) return
    rail.scrollTo({ left: rail.scrollWidth, behavior: 'smooth' })
    isNearRightEdge.current = true
    setShowNewActivity(false)
  }

  const toggleGroup = (groupKey: string, button: HTMLButtonElement) => {
    setExpandedGroupKey((currentKey) => {
      if (currentKey === groupKey) return null

      const rail = railRef.current
      if (rail) {
        const railBounds = rail.getBoundingClientRect()
        const cardBounds = button.getBoundingClientRect()
        const visibleLeft = railBounds.left + 20
        const visibleRight = railBounds.right - 20

        if (cardBounds.left < visibleLeft) {
          rail.scrollBy({ left: cardBounds.left - visibleLeft, behavior: 'smooth' })
        } else if (cardBounds.right > visibleRight) {
          rail.scrollBy({ left: cardBounds.right - visibleRight, behavior: 'smooth' })
        }
      }
      return groupKey
    })
  }

  if (trajectory.isPending && !trajectory.data) {
    return <LoadingState label="Loading workflow trajectory..." />
  }
  if (trajectory.isError && !trajectory.data) {
    return (
      <div className="px-5 py-8 text-sm text-red-800" role="alert">
        Workflow trajectory unavailable.
      </div>
    )
  }
  if (!trajectory.data) return null

  return (
    <div>
      {workflow && (
        <>
          <div className="flex flex-col gap-3 border-b border-line px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="min-w-0">
              <p className="text-sm font-semibold text-ink-950">
                {workflow.agentName} · {workflow.workflowType === 'CUSTOMER_REPORTING'
                  ? 'Customer Reporting'
                  : 'Customer Support'}
              </p>
              <p className="mt-1 truncate font-mono text-xs text-muted">{workflow.workflowId}</p>
            </div>
            <Link
              className="inline-flex w-fit shrink-0 items-center gap-1 text-xs font-medium text-accent hover:text-accent/80"
              to={`/workflows/${encodeURIComponent(workflowId)}`}
            >
              Open trajectory <ArrowUpRight aria-hidden="true" size={14} />
            </Link>
          </div>

          <div className="grid gap-4 border-b border-line px-5 py-4 sm:grid-cols-2 xl:grid-cols-4">
            <div>
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted">
                Workflow state
              </p>
              <p className="mt-1 text-sm font-semibold text-ink-950">
                {governanceSummary?.currentState
                  ? formatGovernanceLabel(governanceSummary.currentState)
                  : 'State unavailable'}
              </p>
            </div>
            <div>
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted">
                Latest activity
              </p>
              <p className="mt-1 text-sm font-medium text-ink-900">
                {governanceSummary?.latestActivity?.label ?? 'Activity unavailable'}
              </p>
              {governanceSummary?.latestActivity && (
                <time
                  className="mt-0.5 block text-xs text-muted"
                  dateTime={governanceSummary.latestActivity.timestamp}
                >
                  {formatTimestamp(governanceSummary.latestActivity.timestamp)}
                </time>
              )}
            </div>
            <div>
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted">
                Latest governance
              </p>
              {governanceSummary?.latestGovernanceOutcome ? (
                <span
                  className={`mt-1 inline-flex rounded border px-2 py-1 text-xs font-semibold ${
                    governanceSummary.latestGovernanceOutcome.tone === 'block'
                      ? 'border-red-200 bg-red-50 text-red-800'
                      : governanceSummary.latestGovernanceOutcome.tone === 'attention'
                        ? 'border-amber-200 bg-amber-50 text-amber-800'
                        : governanceSummary.latestGovernanceOutcome.tone === 'allow'
                          ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                          : 'border-slate-200 bg-slate-50 text-slate-700'
                  }`}
                >
                  {governanceSummary.latestGovernanceOutcome.label}
                </span>
              ) : (
                <p className="mt-1 text-sm text-muted">Governance outcome unavailable</p>
              )}
            </div>
            <div>
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted">
                Recorded activity
              </p>
              <p className="mt-1 text-sm font-medium text-ink-900">
                {governanceSummary?.actionCount ?? 0} grouped actions ·{' '}
                {governanceSummary?.eventCount ?? 0} governance events
              </p>
              {governanceSummary?.latestTimestamp && (
                <time
                  className="mt-0.5 block text-xs text-muted"
                  dateTime={governanceSummary.latestTimestamp}
                >
                  Latest {formatTimestamp(governanceSummary.latestTimestamp)}
                </time>
              )}
            </div>
          </div>
        </>
      )}

      {trajectory.data.events.length === 0 ? (
        <EmptyState
          title="No governance events recorded for this workflow"
          description="The workflow summary is available, but its trajectory contains no recorded events."
        />
      ) : (
      <div className="relative">
        {showNewActivity && (
          <button
            className="absolute right-4 top-3 z-10 rounded-md border border-line bg-white px-3 py-1.5 text-xs font-medium text-ink-900 shadow-panel hover:bg-slate-50"
            onClick={scrollToLatest}
            type="button"
          >
            New activity · View latest
          </button>
        )}
        <div
          aria-label="Chronological workflow governance events"
          className="overflow-x-auto overscroll-x-contain px-5 py-5"
          onScroll={handleScroll}
          ref={railRef}
          role="region"
          tabIndex={0}
        >
          <ol className="flex min-w-max items-stretch gap-0">
            {groups.map((group, index) => (
              <li className="flex items-center" key={group.key}>
                <WorkflowActionCard
                  attentionLevel={
                    governanceSummary?.attentionGroupKey === group.key
                      ? governanceSummary.attentionLevel
                      : 'NONE'
                  }
                  expanded={expandedGroupKey === group.key}
                  group={group}
                  onToggle={(button) => toggleGroup(group.key, button)}
                />
                {index < groups.length - 1 && (
                  <span
                    aria-hidden="true"
                    className="flex w-12 shrink-0 items-center justify-center text-slate-400 sm:w-16"
                  >
                    <ArrowRight size={18} strokeWidth={1.5} />
                  </span>
                )}
              </li>
            ))}
          </ol>
        </div>
      </div>
      )}
      {trajectory.isFetching && (
        <p aria-live="polite" className="border-t border-line px-5 py-2 text-[11px] text-muted">
          Refreshing persisted trajectory…
        </p>
      )}
    </div>
  )
}
