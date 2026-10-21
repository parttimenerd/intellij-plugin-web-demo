export interface ClassInfo {
  fqn: string
  simpleName: string
  methods: string[]
}

export function buildClassUrl(filter: string): string {
  return `/api/classes?filter=${encodeURIComponent(filter)}`
}

export function buildSourceUrl(fqn: string, method?: string): string {
  const base = `/api/source?fqn=${encodeURIComponent(fqn)}`
  return method ? `${base}&method=${encodeURIComponent(method)}` : base
}

export function buildNavigateUrl(fqn: string): string {
  return `/api/navigate?fqn=${encodeURIComponent(fqn)}`
}

export function scrollTopForLine(line: number, lineHeight: number, offset = 80): number {
  return (line - 1) * lineHeight - offset
}

export type WsMessage =
  | { type: 'filter'; filter: string }
  | { type: 'reload' }
  | null

export function parseWsMessage(data: string): WsMessage {
  try {
    const msg = JSON.parse(data)
    if (typeof msg.filter === 'string') return { type: 'filter', filter: msg.filter }
    if (msg.reload === true) return { type: 'reload' }
    return null
  } catch {
    return null
  }
}
