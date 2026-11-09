<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import { createHighlighterCore, type HighlighterCore } from 'shiki/core'
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript'
import { type ClassInfo, buildClassUrl, buildSourceUrl, buildNavigateUrl, parseWsMessage } from './classBrowser'

let highlighter: HighlighterCore | null = null
async function getHighlighter() {
  if (!highlighter)
    highlighter = await createHighlighterCore({
      langs: [import('shiki/langs/java.mjs')],
      themes: [import('shiki/themes/github-light.mjs'), import('shiki/themes/github-dark.mjs')],
      engine: createJavaScriptRegexEngine(),
    })
  return highlighter
}

const filter    = ref('')
const classes   = ref<ClassInfo[]>([])
const sourceEl  = ref<HTMLDivElement | null>(null)
const theme     = ref<'github-light' | 'github-dark'>('github-light')

// rendered HTML per fqn
const sourceMap = ref<Map<string, string>>(new Map())

// Debounced fetch on filter change
let filterTimer: ReturnType<typeof setTimeout>
watch(filter, (val) => {
  clearTimeout(filterTimer)
  filterTimer = setTimeout(() => fetchClasses(val), 300)
})

async function fetchClasses(val: string) {
  const res = await fetch(buildClassUrl(val))
  if (!res.ok) return
  classes.value = await res.json()
  // fetch source for every matched class in parallel
  await Promise.all(classes.value.map(c => loadSource(c.fqn)))
}

async function loadSource(fqn: string) {
  const res = await fetch(buildSourceUrl(fqn))
  if (!res.ok) return
  const data = await res.json()
  const hl = await getHighlighter()
  const html = hl.codeToHtml(data.source, { lang: 'java', theme: theme.value })
  sourceMap.value = new Map(sourceMap.value).set(fqn, html)
}

async function scrollToClass(fqn: string, method?: string) {
  await nextTick()
  const block = sourceEl.value?.querySelector<HTMLElement>(`[data-fqn="${CSS.escape(fqn)}"]`)
  if (!block) return
  if (!method) { block.scrollIntoView({ behavior: 'smooth', block: 'start' }); return }
  // find method line within the block's <pre> text
  const res = await fetch(buildSourceUrl(fqn, method))
  if (!res.ok) return
  const data = await res.json()
  const lineHeight = parseFloat(getComputedStyle(block).lineHeight) || 20
  sourceEl.value!.scrollTop = block.offsetTop + (data.line - 1) * lineHeight - 80
}

function navigate(fqn: string) {
  fetch(buildNavigateUrl(fqn), { method: 'POST' })
    .then(() => scrollToClass(fqn))
}

// reload debounce — PSI fires many events per keystroke; coalesce on the frontend too
let reloadTimer: ReturnType<typeof setTimeout>
function scheduleReload() {
  clearTimeout(reloadTimer)
  reloadTimer = setTimeout(() => fetchClasses(filter.value), 300)
}

function connectWs() {
  const ws = new WebSocket(`ws://${location.host}/ws/filter`)
  ws.onmessage = (e) => {
    const msg = parseWsMessage(e.data)
    if (msg?.type === 'filter') filter.value = msg.filter
    else if (msg?.type === 'reload') scheduleReload()
  }
  ws.onclose = () => setTimeout(connectWs, 2000)
}

onMounted(async () => {
  const t = await fetch('/api/theme').then(r => r.text())
  theme.value = t === 'dark' ? 'github-dark' : 'github-light'
  fetchClasses('')
  connectWs()
})
</script>

<template>
  <div class="layout" :class="theme === 'github-dark' ? 'dark' : 'light'">
    <div class="sidebar">
      <input v-model="filter" class="search" placeholder="Filter classes or methods…" />

      <div class="class-list">
        <div v-for="c in classes" :key="c.fqn" class="class-box">
          <div class="class-name" @click="navigate(c.fqn)">{{ c.simpleName }}</div>
          <div class="package">{{ c.fqn }}</div>
          <div class="methods">
            <span v-for="m in c.methods" :key="m" class="chip"
                  @click="scrollToClass(c.fqn, m)">{{ m }}</span>
          </div>
        </div>
      </div>
    </div>

    <div ref="sourceEl" class="source-pane">
      <template v-for="c in classes" :key="c.fqn">
        <div v-if="sourceMap.has(c.fqn)"
             :data-fqn="c.fqn"
             class="source-block"
             v-html="sourceMap.get(c.fqn)" />
      </template>
    </div>
  </div>
</template>

<style scoped>
.layout   { display: flex; height: auto; font-family: monospace; }
.sidebar  { width: 320px; display: flex; flex-direction: column; border-right: 1px solid #ddd; overflow: hidden; }
.search   { padding: 8px; font-size: 13px; border: none; border-bottom: 1px solid #ddd; outline: none; }
.class-list { overflow-y: auto; flex: 1; }
.class-box  { padding: 8px; border-bottom: 1px solid #eee; }
.class-name { font-weight: bold; font-size: 13px; cursor: pointer; color: #0066cc; }
.class-name:hover { text-decoration: underline; }
.package    { font-size: 11px; color: #888; margin-bottom: 4px; }
.methods    { display: flex; flex-wrap: wrap; gap: 4px; }
.chip       { font-size: 11px; background: #f0f0f0; border-radius: 3px;
              padding: 2px 6px; cursor: pointer; }
.chip:hover { background: #dde; }

.source-pane  { flex: 1; overflow-y: auto; }
.source-block { margin-bottom: 12px; }
.source-block :deep(pre) { margin: 0; padding: 16px; line-height: 1.5; font-size: 13px; width: auto; overflow-x: auto; }

/* dark sidebar */
.dark .sidebar    { background: #1e1e1e; border-right-color: #333; }
.dark .search     { background: #1e1e1e; color: #ccc; border-bottom-color: #333; }
.dark .class-box  { border-bottom-color: #2a2a2a; }
.dark .class-name { color: #6db3f2; }
.dark .package    { color: #666; }
.dark .chip       { background: #2d2d2d; color: #ccc; }
.dark .chip:hover { background: #3a3a5a; }
</style>
