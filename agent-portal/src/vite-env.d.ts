/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_API_PROXY_TARGET?: string
  readonly VITE_GUARDIAN_URL?: string
  readonly VITE_AGENT_PORTAL_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
