<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import { codeToHtml } from 'shiki'
import { type ClassInfo, buildClassUrl, buildSourceUrl, buildNavigateUrl, scrollTopForLine, parseWsMessage } from './classBrowser'

const filter    = ref('')
const classes   = ref<ClassInfo[]>([])
const source    = ref('// click a class or method')
const sourceEl  = ref<HTMLDivElement | null>(null)
const theme     = ref<'github-light' | 'github-dark'>('github-light')

// track current source view for reload
let currentFqn: string | null = null
let currentMethod: string | undefined

// Debounced fetch on filter change
let filterTimer: ReturnType<typeof setTimeout>
watch(filter, (val) => {
  clearTimeout(filterTimer)
  filterTimer = setTimeout(() => fetchClasses(val), 300)
})

async function fetchClasses(val: string) {
  const res = await fetch(buildClassUrl(val))
  classes.value = await res.json()
}

async function showSource(fqn: string, method?: string) {
  currentFqn = fqn
  currentMethod = method
  const res = await fetch(buildSourceUrl(fqn, method))
  if (!res.ok) { source.value = '// source not available'; return }
  const data = await res.json()
  source.value = await codeToHtml(data.source, { lang: 'java', theme: theme.value })
  await nextTick()
  if (sourceEl.value)
    sourceEl.value.scrollTop = scrollTopForLine(data.line, parseFloat(getComputedStyle(sourceEl.value).lineHeight) || 20)
}

function navigate(fqn: string) {
  fetch(buildNavigateUrl(fqn), { method: 'POST' })
    .then(() => showSource(fqn))
}

// reload debounce — PSI fires many events per keystroke; coalesce on the frontend too
let reloadTimer: ReturnType<typeof setTimeout>
function scheduleReload() {
  clearTimeout(reloadTimer)
  reloadTimer = setTimeout(() => {
    fetchClasses(filter.value)
    if (currentFqn) showSource(currentFqn, currentMethod)
  }, 300)
}

onMounted(async () => {
  const t = await fetch('/api/theme').then(r => r.text())
  theme.value = t === 'dark' ? 'github-dark' : 'github-light'
  fetchClasses('')
  const ws = new WebSocket(`ws://${location.host}/ws/filter`)
  ws.onmessage = (e) => {
    const msg = parseWsMessage(e.data)
    if (msg?.type === 'filter') filter.value = msg.filter
    else if (msg?.type === 'reload') scheduleReload()
  }
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
                  @click="showSource(c.fqn, m)">{{ m }}</span>
          </div>
        </div>
      </div>
    </div>

    <div ref="sourceEl" class="source" v-html="source" />
  </div>
</template>

<style scoped>
.layout   { display: flex; height: 100vh; font-family: monospace; }
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
.source     { flex: 1; overflow: auto; padding: 0; font-size: 13px; }
/* shiki wraps output in <pre> with inline styles — let it fill the pane */
.source :deep(pre) { margin: 0; padding: 16px; min-height: 100%; line-height: 1.5; }

/* dark sidebar */
.dark .sidebar  { background: #1e1e1e; border-right-color: #333; }
.dark .search   { background: #1e1e1e; color: #ccc; border-bottom-color: #333; }
.dark .class-box { border-bottom-color: #2a2a2a; }
.dark .class-name { color: #6db3f2; }
.dark .package  { color: #666; }
.dark .chip     { background: #2d2d2d; color: #ccc; }
.dark .chip:hover { background: #3a3a5a; }
</style>
