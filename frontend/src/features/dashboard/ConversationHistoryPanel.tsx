import { useMemo, useState } from 'react'
import { ArrowRight, ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../../components/feedback/EmptyState'
import { ErrorState } from '../../components/feedback/ErrorState'
import { LoadingState } from '../../components/feedback/LoadingState'
import { Panel } from '../../components/ui/Panel'
import type { UseQueryResult } from '@tanstack/react-query'
import { useWorkflowTrajectories } from '../../hooks/useWorkflows'
import type { WorkflowSummaryResponse } from '../../types/workflow'
import {
  getWorkflowGovernanceSummary,
  groupTrajectoryEvents,
} from './workflowPresentation'
import { WorkflowActionCard } from './WorkflowActionCard'
import { groupWorkflowsByConversation, workflowTypeLabel } from './conversationHistory'

function formatTimestamp(timestamp: string): string {
  const date = new Date(timestamp)
  if (Number.isNaN(date.getTime())) return 'Time unavailable'
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function WorkflowHistoryCard({
  workflow,
  trajectoryQuery,
}: {
  workflow: WorkflowSummaryResponse
  trajectoryQuery: ReturnType<typeof useWorkflowTrajectories>[number]
}) {
  const [expandedGroupKey, setExpandedGroupKey] = useState<string | null>(null)
  const trajectory = trajectoryQuery.data
  const groups = groupTrajectoryEvents(trajectory?.events ?? [])
  const governanceSummary = trajectory ? getWorkflowGovernanceSummary(trajectory) : null
  const workflowId = workflow.workflowId

  return (
    <article className="overflow-hidden rounded-md border border-line bg-white">
      <header className="flex flex-col gap-3 border-b border-line px-4 py-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-ink-950">
            {workflow.agentName} · {workflowTypeLabel(workflow.workflowType)}
          </h3>
          <p className="mt-1 truncate font-mono text-xs text-muted">{workflowId}</p>
          <p className="mt-1 text-xs text-muted">
            Started {formatTimestamp(workflow.startedAt)}
          </p>
        </div>
        <Link
          className="inline-flex w-fit shrink-0 items-center gap-1 text-xs font-medium text-accent hover:text-accent/80"
          to={`/workflows/${encodeURIComponent(workflowId)}`}
        >
          Open trajectory <ArrowUpRight aria-hidden="true" size={14} />
        </Link>
      </header>

      {trajectoryQuery.isPending && !trajectory && (
        <div className="px-4 py-5">
          <LoadingState label="Loading persisted governance trail..." />
        </div>
      )}
      {trajectoryQuery.isError && !trajectory && (
        <div className="p-4">
          <ErrorState message={`The persisted trajectory for workflow ${workflowId} could not be loaded.`} />
        </div>
      )}
      {trajectory && (
        <>
          {trajectoryQuery.isError && (
            <div className="p-4 pb-0">
              <ErrorState message="The latest trajectory refresh failed. Previously loaded events remain visible." />
            </div>
          )}
          <div className="grid gap-4 border-b border-line px-4 py-4 sm:grid-cols-2">
            <div>
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted">Latest activity</p>
              <p className="mt-1 text-sm font-medium text-ink-900">
                {governanceSummary?.latestActivity?.label ?? 'No activity recorded'}
              </p>
              {governanceSummary?.latestActivity && (
                <time className="mt-0.5 block text-xs text-muted" dateTime={governanceSummary.latestActivity.timestamp}>
                  {formatTimestamp(governanceSummary.latestActivity.timestamp)}
                </time>
              )}
            </div>
            <div>
              <p className="text-[11px] font-medium uppercase tracking-wide text-muted">Latest governance</p>
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
                <p className="mt-1 text-sm text-muted">No governance outcome recorded</p>
              )}
            </div>
          </div>

          {groups.length === 0 ? (
            <EmptyState
              title="No governance events recorded"
              description="This workflow summary exists, but its persisted trajectory contains no events."
            />
          ) : (
            <div className="px-4 py-4">
              <p className="mb-3 text-[11px] font-medium uppercase tracking-wide text-muted">
                Persisted governance event trail
              </p>
              <ol className="flex flex-wrap items-center gap-y-3">
                {groups.map((group, index) => {
                  const key = `${workflowId}:${group.key}`
                  return (
                    <li className="flex items-center" key={key}>
                      <WorkflowActionCard
                        expanded={expandedGroupKey === key}
                        group={group}
                        onToggle={() => setExpandedGroupKey((current) => current === key ? null : key)}
                      />
                      {index < groups.length - 1 && (
                        <span aria-hidden="true" className="flex w-8 shrink-0 items-center justify-center text-slate-400">
                          <ArrowRight size={16} strokeWidth={1.5} />
                        </span>
                      )}
                    </li>
                  )
                })}
              </ol>
            </div>
          )}
        </>
      )}
    </article>
  )
}

export function ConversationHistoryPanel({
  summaries,
  summariesPending,
  summariesError,
}: {
  summaries: WorkflowSummaryResponse[]
  summariesPending: boolean
  summariesError: UseQueryResult<WorkflowSummaryResponse[]>['error'] | null
}) {
  const conversations = useMemo(() => groupWorkflowsByConversation(summaries), [summaries])
  const [requestedConversationId, setRequestedConversationId] = useState('')
  const selectedConversation = conversations.find(
    (conversation) => conversation.conversationId === requestedConversationId,
  ) ?? conversations[0]
  const selectedWorkflows = selectedConversation?.workflows ?? []
  const workflowIds = selectedWorkflows.map((workflow) => workflow.workflowId)
  const trajectoryQueries = useWorkflowTrajectories(workflowIds)

  return (
    <Panel
      title="Conversation history"
      description="Select a persisted conversation to view its workflows and governance event trails in start-time order."
    >
      {summariesPending && summaries.length === 0 ? (
        <div className="px-5 py-6">
          <LoadingState label="Loading conversation-linked workflow summaries..." />
        </div>
      ) : summariesError && summaries.length === 0 ? (
        <div className="p-5">
          <ErrorState message="Persisted workflow summaries could not be loaded, so conversation history is unavailable." />
        </div>
      ) : conversations.length === 0 ? (
        <EmptyState
          title="No conversation-linked workflows"
          description="Workflows with a persisted conversation ID will appear here. Standalone workflows remain separate."
        />
      ) : (
        <>
          <div className="border-b border-line px-5 py-4">
            <label className="block text-xs font-medium text-slate-700" htmlFor="conversation-history-select">
              Conversation
            </label>
            <select
              className="mt-1.5 block w-full rounded-md border border-line bg-white px-3 py-2 text-sm text-ink-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent sm:max-w-2xl"
              id="conversation-history-select"
              onChange={(event) => setRequestedConversationId(event.target.value)}
              value={selectedConversation?.conversationId ?? ''}
            >
              {conversations.map((conversation) => (
                <option key={conversation.conversationId} value={conversation.conversationId}>
                  {conversation.conversationId} · {conversation.workflows.length} workflow
                  {conversation.workflows.length === 1 ? '' : 's'}
                </option>
              ))}
            </select>
          </div>
          <div className="space-y-4 p-4 sm:p-5">
            {selectedWorkflows.map((workflow, index) => (
              <div className="relative" key={workflow.workflowId}>
                {index > 0 && (
                  <span
                    aria-hidden="true"
                    className="absolute -top-4 left-5 h-4 border-l border-slate-300"
                  />
                )}
                <WorkflowHistoryCard
                  trajectoryQuery={trajectoryQueries[index]}
                  workflow={workflow}
                />
              </div>
            ))}
          </div>
        </>
      )}
    </Panel>
  )
}
