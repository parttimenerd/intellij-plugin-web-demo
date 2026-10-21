<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import { codeToHtml } from 'shiki'

interface ClassInfo {
  fqn: string
  simpleName: string
  methods: string[]
}

const filter   = ref('')
const classes  = ref<ClassInfo[]>([])
const source   = ref('// click a class or method')
const sourceEl = ref<HTMLDivElement | null>(null)

// Debounced fetch on filter change
let timer: ReturnType<typeof setTimeout>
watch(filter, (val) => {
  clearTimeout(timer)
  timer = setTimeout(() => fetchClasses(val), 300)
})

async function fetchClasses(val: string) {
  if (val.length < 2) { classes.value = []; return }
  const res = await fetch(`/api/classes?filter=${encodeURIComponent(val)}`)
  classes.value = await res.json()
}

async function showSource(fqn: string, method?: string) {
  const url = method
    ? `/api/source?fqn=${encodeURIComponent(fqn)}&method=${encodeURIComponent(method)}`
    : `/api/source?fqn=${encodeURIComponent(fqn)}`
  const res = await fetch(url)
  if (!res.ok) { source.value = '// source not available'; return }
  const data = await res.json()
  // shiki renders fully self-contained HTML with inline styles — no CSS import needed
  source.value = await codeToHtml(data.source, { lang: 'java', theme: 'github-light' })
  await nextTick()
  scrollToLine(data.line)
}

function scrollToLine(line: number) {
  if (!sourceEl.value) return
  const lineHeight = parseFloat(getComputedStyle(sourceEl.value).lineHeight) || 20
  sourceEl.value.scrollTop = (line - 1) * lineHeight - 80
}

function navigate(fqn: string) {
  fetch(`/api/navigate?fqn=${encodeURIComponent(fqn)}`, { method: 'POST' })
    .then(() => showSource(fqn))
}

onMounted(() => {
  const ws = new WebSocket(`ws://${location.host}/ws/filter`)
  ws.onmessage = (e) => { filter.value = JSON.parse(e.data).filter }
})
</script>

<template>
  <div class="layout">
    <div class="sidebar">
      <input v-model="filter" class="search" placeholder="Filter classes or methods (min 2 chars)" />

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
</style>
