import { describe, it, expect } from 'vitest'
import { buildClassUrl, buildSourceUrl, buildNavigateUrl, scrollTopForLine, parseWsMessage } from './classBrowser'

describe('buildClassUrl', () => {
  it('empty filter returns all-classes url', () => {
    expect(buildClassUrl('')).toBe('/api/classes?filter=')
  })
  it('encodes filter value', () => {
    expect(buildClassUrl('com.example.Foo')).toBe('/api/classes?filter=com.example.Foo')
  })
  it('encodes special characters', () => {
    expect(buildClassUrl('foo bar')).toBe('/api/classes?filter=foo%20bar')
  })
})

describe('buildSourceUrl', () => {
  it('without method', () => {
    expect(buildSourceUrl('com.example.Foo')).toBe('/api/source?fqn=com.example.Foo')
  })
  it('with method appends method param', () => {
    expect(buildSourceUrl('com.example.Foo', 'bar')).toBe('/api/source?fqn=com.example.Foo&method=bar')
  })
  it('encodes fqn dots (no encoding needed — dots are safe)', () => {
    expect(buildSourceUrl('com.example.Foo')).toContain('com.example.Foo')
  })
})

describe('buildNavigateUrl', () => {
  it('returns navigate url with fqn', () => {
    expect(buildNavigateUrl('com.example.Foo')).toBe('/api/navigate?fqn=com.example.Foo')
  })
})

describe('scrollTopForLine', () => {
  it('line 1 scrolls to -offset (clamped by browser, fine here)', () => {
    expect(scrollTopForLine(1, 20)).toBe(-80)
  })
  it('line 5 scrolls to 4 * lineHeight - 80', () => {
    expect(scrollTopForLine(5, 20)).toBe(0)
  })
  it('uses custom offset', () => {
    expect(scrollTopForLine(3, 20, 0)).toBe(40)
  })
  it('fractional lineHeight', () => {
    expect(scrollTopForLine(2, 18.5)).toBe(18.5 - 80)
  })
})

describe('parseWsMessage', () => {
  it('returns filter string from valid message', () => {
    expect(parseWsMessage('{"filter":"com.example.Foo"}')).toBe('com.example.Foo')
  })
  it('returns empty string for empty filter', () => {
    expect(parseWsMessage('{"filter":""}')).toBe('')
  })
  it('returns null for invalid JSON', () => {
    expect(parseWsMessage('not json')).toBeNull()
  })
  it('returns null if filter key missing', () => {
    expect(parseWsMessage('{"other":"value"}')).toBeNull()
  })
  it('returns null if filter is not a string', () => {
    expect(parseWsMessage('{"filter":42}')).toBeNull()
  })
})
