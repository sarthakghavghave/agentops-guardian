import { Bot } from 'lucide-react'

const agents: {
  name: string
  tools: string[]
  internalOperations?: string[]
}[] = [
  {
    name: 'CustomerSupportAgent',
    tools: [
      'getCustomer',
      'searchCustomersByName',
      'getCustomerOrders',
      'getOrder',
      'getOrderItems',
      'returnOrder',
      'getProduct',
      'getCustomerData',
    ],
  },
  {
    name: 'CustomerReportAgent',
    tools: ['getCustomerData', 'generateReport'],
  },
  {
    name: 'EmailAgent',
    tools: ['sendEmail'],
    internalOperations: ['Read Conversation History'],
  },
]

export function AgentCatalog({ observedAgentNames }: { observedAgentNames: Set<string> }) {
  return (
    <section aria-labelledby="agent-catalog-heading" className="agent-catalog">
      <div className="side-panel-heading">
        <h2 id="agent-catalog-heading">Agents &amp; tools</h2>
        <span>Catalog</span>
      </div>
      <p className="catalog-note">
        Observed means an event was received for this conversation; it does not indicate current execution or backend health.
      </p>
      <div className="agent-list">
        {agents.map((agent) => {
          const observed = observedAgentNames.has(agent.name)
          return (
            <article className={`agent-entry${observed ? ' agent-entry-observed' : ''}`} key={agent.name}>
              <div className="agent-entry-heading">
                <span aria-hidden="true" className="agent-icon"><Bot size={14} /></span>
                <h3>{agent.name}</h3>
                <span className={`agent-state${observed ? ' agent-state-observed' : ''}`}>
                  {observed ? 'Observed' : 'Available'}
                </span>
              </div>
              <p className="agent-tools">{agent.tools.join(' · ')}</p>
              {agent.internalOperations && (
                <p className="agent-tools agent-internal-operations">
                  Internal: {agent.internalOperations.join(' · ')}
                </p>
              )}
            </article>
          )
        })}
      </div>
    </section>
  )
}
