import { useState } from 'react'
import { ArrowLeft, ChevronDown, ChevronRight, Send } from 'lucide-react'

type Scenario = {
  id: string
  name: string
  messages: string[]
}

const scenarios: Scenario[] = [
  {
    id: 'report-generation',
    name: 'Consecutive tasks: Report then Email',
    messages: [
      'Generate an analytical report of customers in North Michael.',
      'Mail the analytical report to ghavghavess@rknec.edu with a short professional message.',
    ],
  },
  {
    id: 'email-sending',
    name: 'Email Sending',
    messages: [
      "Send an email to ghavghavess@rknec.edu with a professional message: 'Greetings, your meeting is scheduled at 4 PM.' Please make the message polished and courteous, and include a suitable subject.",
    ],
  },
  {
    id: 'sensitive-data-leak',
    name: 'Sensitive data leak',
    messages: [
      'Give me the email ID of Michael Fletcher.',
      "Send an email to ghavghavess@rknec.edu 'This is invalid email:' mentioning the actual email id of Michael Fletcher.",
    ],
  },
  {
    id: 'task-chaining',
    name: 'Task Chaining: Report + Email',
    messages: [
      'Generate an analytical report of customers in North Michael and email it to ghavghavess@rknec.edu.',
    ],
  },
]

type ScenarioSamplesPanelProps = {
  onSelectMessage: (message: string) => void
  onSendMessage: (message: string) => void
  disabled: boolean
}

export function ScenarioSamplesPanel({
  onSelectMessage,
  onSendMessage,
  disabled,
}: ScenarioSamplesPanelProps) {
  const [expanded, setExpanded] = useState(false)
  const [selectedScenario, setSelectedScenario] = useState<Scenario | null>(null)

  function toggleExpanded() {
    setExpanded((current) => {
      if (current) setSelectedScenario(null)
      return !current
    })
  }

  return (
    <section aria-labelledby="scenario-samples-heading" className="scenario-panel">
      <button
        aria-controls="scenario-samples-content"
        aria-expanded={expanded}
        className="scenario-panel-heading"
        onClick={toggleExpanded}
        type="button"
      >
        <span>
          <strong id="scenario-samples-heading">Scenario samples</strong>
          <small>Try a guided demonstration</small>
        </span>
        {expanded ? <ChevronDown aria-hidden="true" size={16} /> : <ChevronRight aria-hidden="true" size={16} />}
      </button>

      {expanded && (
        <div className="scenario-panel-content" id="scenario-samples-content">
          {selectedScenario ? (
            <>
              <button
                className="scenario-back"
                onClick={() => setSelectedScenario(null)}
                type="button"
              >
                <ArrowLeft aria-hidden="true" size={13} />
                All scenarios
              </button>
              <h3 className="scenario-detail-title">{selectedScenario.name}</h3>
              <div className="scenario-message-list">
                {selectedScenario.messages.map((message, index) => (
                  <div className="scenario-message-row" key={`${selectedScenario.id}-${index}`}>
                    <button
                      className="scenario-message-text"
                      disabled={disabled}
                      onClick={() => onSelectMessage(message)}
                      type="button"
                    >
                      {message}
                    </button>
                    <button
                      aria-label={`Send sample message ${index + 1}`}
                      className="scenario-send-button"
                      disabled={disabled}
                      onClick={() => onSendMessage(message)}
                      title="Send this message to the agent"
                      type="button"
                    >
                      <Send aria-hidden="true" size={13} />
                    </button>
                  </div>
                ))}
              </div>
              <p className="scenario-panel-hint">Select a message to edit it, or send it directly.</p>
            </>
          ) : (
            <div className="scenario-list">
              {scenarios.map((scenario) => (
                <button
                  className="scenario-list-item"
                  key={scenario.id}
                  onClick={() => setSelectedScenario(scenario)}
                  type="button"
                >
                  <span>{scenario.name}</span>
                  <ChevronRight aria-hidden="true" size={14} />
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </section>
  )
}
