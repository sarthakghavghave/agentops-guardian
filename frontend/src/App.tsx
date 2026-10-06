import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AppLayout } from './layouts/AppLayout'
import { DashboardPage } from './pages/DashboardPage'
import { InterventionsPage } from './pages/InterventionsPage'
import { InterventionDetailsPage } from './pages/InterventionDetailsPage'
import { PoliciesPage } from './pages/PoliciesPage'
import { PolicyDetailsPage } from './pages/PolicyDetailsPage'
import { PolicyAuthoringPage } from './pages/PolicyAuthoringPage'
import { WorkflowDetailsPage } from './pages/WorkflowDetailsPage'
import { WorkflowsPage } from './pages/WorkflowsPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<AppLayout />}>
            <Route index element={<Navigate replace to="/dashboard" />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/workflows" element={<WorkflowsPage />} />
            <Route path="/workflows/:workflowId" element={<WorkflowDetailsPage />} />
            <Route path="/interventions" element={<InterventionsPage />} />
            <Route path="/interventions/:interventionId" element={<InterventionDetailsPage />} />
            <Route path="/policies" element={<PoliciesPage />} />
            <Route path="/policies/new" element={<PolicyAuthoringPage />} />
            <Route path="/policies/:policyId/edit" element={<PolicyAuthoringPage />} />
            <Route path="/policies/:policyId" element={<PolicyDetailsPage />} />
            <Route path="*" element={<Navigate replace to="/dashboard" />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
