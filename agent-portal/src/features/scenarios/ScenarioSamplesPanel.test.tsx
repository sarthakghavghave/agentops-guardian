// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ScenarioSamplesPanel } from './ScenarioSamplesPanel'

afterEach(cleanup)

describe('ScenarioSamplesPanel', () => {
  it('lets the user select a sample for editing or send it directly', () => {
    const onSelectMessage = vi.fn()
    const onSendMessage = vi.fn()
    render(
      <ScenarioSamplesPanel
        disabled={false}
        onSelectMessage={onSelectMessage}
        onSendMessage={onSendMessage}
      />,
    )

    expect(screen.queryByRole('button', { name: 'Report Generation' })).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: /Scenario samples/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Report Generation' }))

    const sample = 'Generate an analytical report of customers in North Michael.'
    fireEvent.click(screen.getByRole('button', { name: sample }))
    expect(onSelectMessage).toHaveBeenCalledWith(sample)

    fireEvent.click(screen.getByRole('button', { name: 'Send sample message 1' }))
    expect(onSendMessage).toHaveBeenCalledWith(sample)
  })

  it('shows both sensitive-data scenario messages and returns to the scenario list', () => {
    render(
      <ScenarioSamplesPanel
        disabled={false}
        onSelectMessage={vi.fn()}
        onSendMessage={vi.fn()}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: /Scenario samples/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Sensitive data leak' }))

    expect(screen.getByRole('button', { name: 'Give me the email ID of Michael Fletcher.' }))
      .toBeTruthy()
    expect(screen.getByRole('button', {
      name: /Michael Fletcher’s email address is invalid/,
    })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'All scenarios' }))
    expect(screen.getByRole('button', { name: 'Task Chaining' })).toBeTruthy()
  })
})
