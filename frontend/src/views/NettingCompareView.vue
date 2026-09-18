<template>
  <div class="page">
    <h2 class="page-title">轧差对比（双边 vs 多边）</h2>
    <p class="page-desc">对同一批 OPEN 义务分别推演双边与多边轧差，仅试算不落地，推演后义务仍保持 OPEN</p>

    <div class="card-panel">
      <div class="toolbar">
        <el-date-picker v-model="settleDate" type="date" value-format="YYYY-MM-DD" placeholder="交割日" />
        <el-select v-model="currency" style="width:120px">
          <el-option label="USD" value="USD" />
          <el-option label="CNY" value="CNY" />
          <el-option label="EUR" value="EUR" />
        </el-select>
        <el-button type="primary" :disabled="!auth.isOperator" :loading="running" @click="runCompare">
          运行对比推演
        </el-button>
        <el-tag v-if="!auth.isOperator" type="info">只读账号不可运行</el-tag>
      </div>
    </div>

    <template v-if="result">
      <div class="summary card-panel" style="margin-top:16px">
        <div class="stat">
          <div class="label">OPEN 义务笔数</div>
          <div class="value">{{ result.obligations.length }}</div>
        </div>
        <div class="stat">
          <div class="label">义务总额</div>
          <div class="value">{{ fmt(result.grossAmount) }}</div>
        </div>
        <div class="stat">
          <div class="label">双边净额结算总量</div>
          <div class="value">{{ fmt(result.bilateral.totalNetAmount) }}</div>
        </div>
        <div class="stat">
          <div class="label">多边净额结算总量</div>
          <div class="value">{{ fmt(result.multilateral.totalNetAmount) }}</div>
        </div>
        <div class="stat">
          <div class="label">多边相对双边压降</div>
          <div class="value">{{ compression }}</div>
        </div>
      </div>

      <div class="compare-grid" style="margin-top:16px">
        <div class="card-panel">
          <div class="toolbar" style="justify-content:space-between">
            <strong>双边轧差结果</strong>
            <span class="side-note">{{ result.bilateral.positions.length }} 个对手方对</span>
          </div>
          <el-table :data="result.bilateral.positions" stripe style="margin-top:12px">
            <el-table-column label="付款方" min-width="150">
              <template #default="{ row }">
                <div>{{ nameOf(row.payerMemberId) }}</div>
                <span class="mono muted">{{ shortId(row.payerMemberId) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="收款方" min-width="150">
              <template #default="{ row }">
                <div>{{ nameOf(row.payeeMemberId) }}</div>
                <span class="mono muted">{{ shortId(row.payeeMemberId) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="currency" label="币种" width="80" />
            <el-table-column label="净额（付 → 收）" min-width="140" align="right">
              <template #default="{ row }">{{ fmt(row.netAmount) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="card-panel">
          <div class="toolbar" style="justify-content:space-between">
            <strong>多边轧差结果</strong>
            <span class="side-note">ΣnetAmount = {{ result.multilateral.sumNetAmount }}</span>
          </div>
          <el-table :data="result.multilateral.positions" stripe style="margin-top:12px">
            <el-table-column label="会员" min-width="150">
              <template #default="{ row }">
                <div>{{ nameOf(row.memberId) }}</div>
                <span class="mono muted">{{ shortId(row.memberId) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="currency" label="币种" width="80" />
            <el-table-column label="净头寸（正应收/负应付）" min-width="170" align="right">
              <template #default="{ row }">
                <span :class="Number(row.netAmount) >= 0 ? 'pos' : 'neg'">{{ fmt(row.netAmount) }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="card-panel" style="margin-top:16px">
        <div class="toolbar" style="justify-content:space-between">
          <strong>参与推演的 OPEN 义务</strong>
          <el-tag type="success">仅推演，义务仍为 OPEN</el-tag>
        </div>
        <el-table :data="result.obligations" stripe style="margin-top:12px">
          <el-table-column label="付款方" min-width="140">
            <template #default="{ row }">{{ nameOf(row.payerMemberId) }}</template>
          </el-table-column>
          <el-table-column label="收款方" min-width="140">
            <template #default="{ row }">{{ nameOf(row.payeeMemberId) }}</template>
          </el-table-column>
          <el-table-column prop="currency" label="币种" width="80" />
          <el-table-column label="金额" min-width="130" align="right">
            <template #default="{ row }">{{ fmt(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag type="success">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <el-empty v-else-if="!running" description="选择交割日与币种后运行对比推演" style="margin-top:32px" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const settleDate = ref(new Date().toISOString().slice(0, 10))
const currency = ref('USD')
const running = ref(false)
const result = ref(null)
const memberMap = ref({})

const compression = computed(() => {
  if (!result.value) return '-'
  const b = Number(result.value.bilateral.totalNetAmount)
  const m = Number(result.value.multilateral.totalNetAmount)
  if (!b) return '-'
  return ((1 - m / b) * 100).toFixed(1) + '%'
})

function nameOf(id) {
  return memberMap.value[id] || id
}

function shortId(id) {
  return id ? id.slice(0, 8) + '…' : ''
}

function fmt(v) {
  return Number(v || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadMembers() {
  const { data } = await api.get('/members')
  memberMap.value = Object.fromEntries(data.map((x) => [x.memberId, x.name]))
}

async function runCompare() {
  running.value = true
  try {
    const { data } = await api.post('/netting-simulations/compare', {
      settleDate: settleDate.value,
      currency: currency.value
    })
    result.value = data
    ElMessage.success('推演完成：义务状态未改变，仍为 OPEN')
  } catch (e) {
    result.value = null
  } finally {
    running.value = false
  }
}

onMounted(loadMembers)
</script>

<style scoped>
.summary {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
}
.stat .label {
  color: var(--muted);
  font-size: 13px;
}
.stat .value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
}
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.side-note {
  color: var(--muted);
  font-size: 13px;
}
.muted {
  color: var(--muted);
  font-size: 12px;
}
.pos {
  color: #237804;
  font-weight: 600;
}
.neg {
  color: #cf1322;
  font-weight: 600;
}
@media (max-width: 1100px) {
  .summary {
    grid-template-columns: repeat(2, 1fr);
  }
  .compare-grid {
    grid-template-columns: 1fr;
  }
}
</style>
