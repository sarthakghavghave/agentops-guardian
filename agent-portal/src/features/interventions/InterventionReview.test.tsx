// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { approveIntervention, listInterventions, rejectIntervention } from '../../api/interventions'
import { getWorkflowTrajectory } from '../../api/workflows'
import { InterventionReview } from './InterventionReview'
import {
  createResolutionRequest,
  pendingInterventions,
  resolutionOutcome,
} from './interventionReviewState'
import type { GovernanceInterventionResponse, WorkflowTrajectoryResponse } from '../../types/api'

vi.mock('../../api/interventions', () => ({
  approveIntervention: vi.fn(),
  listInterventions: vi.fn(),
  rejectIntervention: vi.fn(),
}))

vi.mock('../../api/workflows', () => ({
  getWorkflowTrajectory: vi.fn(),
}))

const pending: GovernanceInterventionResponse = {
  interventionId: 'intervention-1',
  workflowId: 'workflow-1',
  toolCallId: 'call-1',
  toolName: 'sendEmail',
  agentName: 'EmailAgent',
  workflowType: 'EMAIL_COMMUNICATION',
  capability: 'SEND_EMAIL',
  riskLevel: 'HIGH',
  intervention: 'REQUIRE_APPROVAL',
  status: 'PENDING',
  reason: 'High-risk email requires review.',
  createdAt: '2026-10-16T10:00:00Z',
  resolvedAt: null,
  resolvedBy: null,
  resolutionReason: null,
  conversationId: 'conversation-1',
  taskStatus: null,
  taskMessage: null,
}

const resolved: GovernanceInterventionResponse = {
  ...pending,
  status: 'APPROVED',
  resolvedAt: '2026-10-16T10:05:00Z',
  resolvedBy: 'Reviewer',
  resolutionReason: 'Reviewed the request.',
  taskStatus: 'FAILED',
  taskMessage: 'Task context expired before the operation could resume.',
}

const trajectory: WorkflowTrajectoryResponse = {
  workflowId: pending.workflowId,
  conversationId: pending.conversationId,
  agentName: pending.agentName,
  workflowType: pending.workflowType,
  startedAt: pending.createdAt,
  lastEventAt: '2026-10-16T10:05:00Z',
  currentNodeId: null,
  currentState: null,
  eventCount: 4,
  events: [],
}

const mockedList = vi.mocked(listInterventions)
const mockedApprove = vi.mocked(approveIntervention)
const mockedReject = vi.mocked(rejectIntervention)
const mockedTrajectory = vi.mocked(getWorkflowTrajectory)

beforeEach(() => {
  vi.clearAllMocks()
  mockedList.mockResolvedValue([pending])
  mockedTrajectory.mockResolvedValue(trajectory)
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('intervention review helpers', () => {
  it('filters pending interventions to the active conversation only', () => {
    const otherConversation = {
      ...pending,
      interventionId: 'intervention-2',
      conversationId: 'conversation-2',
    }
    const uncorrelated = {
      ...pending,
      interventionId: 'intervention-3',
      conversationId: null,
    }

    expect(pendingInterventions(
      [pending, resolved, otherConversation, uncorrelated],
      'conversation-1',
    )).toEqual([pending])
    expect(pendingInterventions([pending, otherConversation], 'conversation-2'))
      .toEqual([otherConversation])
    expect(pendingInterventions([pending], null)).toEqual([])
  })

  it('requires both resolution fields and trims submitted values', () => {
    expect(createResolutionRequest('  ', 'reason')).toBeNull()
    expect(createResolutionRequest('Reviewer', '  ')).toBeNull()
    expect(createResolutionRequest(' Reviewer ', ' Reason reviewed ')).toEqual({
      resolvedBy: 'Reviewer',
      resolutionReason: 'Reason reviewed',
    })
  })

  it('uses only returned task status and message for approval outcomes', () => {
    expect(resolutionOutcome(resolved, 'approve')).toContain('Task status: FAILED.')
    expect(resolutionOutcome(resolved, 'approve')).toContain('Task context expired')
    expect(resolutionOutcome({ ...resolved, taskStatus: null, taskMessage: null }, 'approve'))
      .toContain('did not include a task-resumption outcome')
  })

  it('reports the backend rejection and termination outcome', () => {
    expect(resolutionOutcome({
      ...resolved,
      status: 'REJECTED',
      taskStatus: 'TERMINATED',
      taskMessage: 'The pending task was rejected and cannot be resumed.',
    }, 'reject')).toContain('Task status: TERMINATED.')
  })
})

describe('InterventionReview', () => {
  it('shows a loading state and then only pending items with actual task context', async () => {
    let finishLoading: (items: GovernanceInterventionResponse[]) => void = () => undefined
    mockedList.mockReturnValueOnce(new Promise((resolve) => {
      finishLoading = resolve
    }))
    render(<InterventionReview conversationId="conversation-1" />)

    expect(screen.getByRole('status').textContent).toContain('Loading pending interventions')
    finishLoading([pending, resolved])

    expect(await screen.findByText('sendEmail')).toBeTruthy()
    expect(screen.getByText('EmailAgent · EMAIL COMMUNICATION · HIGH risk')).toBeTruthy()
    expect(screen.getByText('High-risk email requires review.')).toBeTruthy()
    expect(screen.queryByText('No pending interventions.')).toBeNull()
    expect(screen.getAllByText('PENDING').length).toBeGreaterThan(0)
  })

  it('submits required approval fields, prevents duplicate resolution, and displays returned outcome', async () => {
    mockedList.mockResolvedValueOnce([pending]).mockResolvedValue([])
    let finishApproval: (value: GovernanceInterventionResponse) => void = () => undefined
    mockedApprove.mockReturnValueOnce(new Promise((resolve) => {
      finishApproval = resolve
    }))
    render(<InterventionReview conversationId="conversation-1" />)

    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    const confirm = screen.getByRole('button', { name: 'Confirm approval' }) as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Resolved by'), { target: { value: ' Reviewer ' } })
    fireEvent.change(screen.getByLabelText('Resolution reason'), {
      target: { value: ' Reviewed details ' },
    })
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)

    expect(await screen.findByRole('button', { name: 'Submitting…' })).toBeTruthy()
    expect(mockedApprove).toHaveBeenCalledWith('intervention-1', {
      resolvedBy: 'Reviewer',
      resolutionReason: 'Reviewed details',
    })
    expect((screen.getByRole('button', { name: 'Submitting…' }) as HTMLButtonElement).disabled).toBe(true)

    finishApproval(resolved)
    expect(await screen.findByText(/Task status: FAILED/)).toBeTruthy()
    expect(await screen.findByText(/Refreshed workflow: 4 persisted events/)).toBeTruthy()
    expect(mockedTrajectory).toHaveBeenCalledWith('workflow-1')
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2))
  })

  it('submits rejection and displays the backend termination result', async () => {
    mockedList.mockResolvedValueOnce([pending]).mockResolvedValue([])
    mockedReject.mockResolvedValueOnce({
      ...resolved,
      status: 'REJECTED',
      taskStatus: 'TERMINATED',
      taskMessage: 'The pending task was rejected and cannot be resumed.',
    })
    render(<InterventionReview conversationId="conversation-1" />)

    fireEvent.click(await screen.findByRole('button', { name: 'Reject' }))
    fireEvent.change(screen.getByLabelText('Resolved by'), { target: { value: 'Reviewer' } })
    fireEvent.change(screen.getByLabelText('Resolution reason'), {
      target: { value: 'Request is not authorized.' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm rejection' }))

    expect(await screen.findByText(/Task status: TERMINATED/)).toBeTruthy()
    expect(mockedReject).toHaveBeenCalledWith('intervention-1', {
      resolvedBy: 'Reviewer',
      resolutionReason: 'Request is not authorized.',
    })
  })

  it('surfaces stale resolution errors without showing a successful outcome', async () => {
    mockedApprove.mockRejectedValueOnce(new Error('Request failed (409 Conflict): Task context expired.'))
    render(<InterventionReview conversationId="conversation-1" />)
    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }))
    fireEvent.change(screen.getByLabelText('Resolved by'), { target: { value: 'Reviewer' } })
    fireEvent.change(screen.getByLabelText('Resolution reason'), { target: { value: 'Reviewed.' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirm approval' }))

    expect((await screen.findByRole('alert')).textContent).toContain('409 Conflict')
    expect(screen.queryByText('Approval response')).toBeNull()
    await waitFor(() => expect(mockedList).toHaveBeenCalledTimes(2))
  })

  it('surfaces list errors with a retry action', async () => {
    mockedList.mockRejectedValueOnce(new Error('Service unavailable'))
    render(<InterventionReview conversationId="conversation-1" />)

    expect((await screen.findByRole('alert')).textContent).toContain('Service unavailable')
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('sendEmail')).toBeTruthy()
  })

  it('shows only the active conversation interventions and switches with the conversation', async () => {
    const otherConversation: GovernanceInterventionResponse = {
      ...pending,
      interventionId: 'intervention-2',
      conversationId: 'conversation-2',
      toolName: 'sendOtherEmail',
    }
    mockedList.mockResolvedValueOnce([pending, otherConversation])
    const { rerender } = render(<InterventionReview conversationId="conversation-1" />)

    expect(await screen.findByText('sendEmail')).toBeTruthy()
    expect(screen.queryByText('sendOtherEmail')).toBeNull()

    rerender(<InterventionReview conversationId="conversation-2" />)
    expect(await screen.findByText('sendOtherEmail')).toBeTruthy()
    expect(screen.queryByText('sendEmail')).toBeNull()
  })

  it('does not show interventions without an active conversation ID', async () => {
    render(<InterventionReview conversationId={null} />)

    expect(await screen.findByText('Start a conversation to see its pending interventions.'))
      .toBeTruthy()
    expect(screen.queryByText('sendEmail')).toBeNull()
  })
})
