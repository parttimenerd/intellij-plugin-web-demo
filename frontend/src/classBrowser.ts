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

export function parseWsMessage(data: string): string | null {
  try {
    const msg = JSON.parse(data)
    return typeof msg.filter === 'string' ? msg.filter : null
  } catch {
    return null
  }
}
