<template>
  <div class="page">
    <h2 class="page-title">轧差对比</h2>
    <p class="page-desc">对同一批 OPEN 义务分别推演双边与多边轧差，仅试算不落地，义务保持 OPEN</p>

    <div class="card-panel">
      <div class="toolbar">
        <el-date-picker v-model="settleDate" type="date" value-format="YYYY-MM-DD" placeholder="交割日" />
        <el-select v-model="currency" style="width:120px">
          <el-option label="USD" value="USD" />
          <el-option label="CNY" value="CNY" />
          <el-option label="EUR" value="EUR" />
        </el-select>
        <el-button type="primary" :disabled="!auth.isOperator" :loading="running" @click="compare">运行对比推演</el-button>
        <span v-if="!auth.isOperator" class="hint">只读用户不可运行</span>
      </div>
    </div>

    <template v-if="result">
      <div class="card-panel" style="margin-top:16px">
        <div class="toolbar" style="justify-content:space-between">
          <div>
            <strong>推演范围</strong>
            <span style="margin-left:12px">交割日 {{ result.settleDate }} · {{ result.currency }} · OPEN 义务 {{ result.obligationCount }} 笔</span>
          </div>
          <el-tag type="info">仅推演 · 义务仍为 OPEN</el-tag>
        </div>
      </div>

      <div class="compare-grid" style="margin-top:16px">
        <div class="card-panel">
          <div class="side-head">
            <strong>双边轧差</strong>
            <span class="hint">逐对手方两两轧差</span>
          </div>
          <div class="side-summary">
            <div class="stat">
              <div class="label">净支付笔数</div>
              <div class="value">{{ result.bilateral.paymentCount }}</div>
            </div>
            <div class="stat">
              <div class="label">总结算量</div>
              <div class="value">{{ result.bilateral.grossAmount }}</div>
            </div>
          </div>
          <el-table :data="result.bilateral.payments" stripe>
            <el-table-column label="付款方" min-width="180">
              <template #default="{ row }">
                <span class="mono">{{ row.fromMemberId }}</span>
                <div>{{ nameOf(row.fromMemberId) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="收款方" min-width="180">
              <template #default="{ row }">
                <span class="mono">{{ row.toMemberId }}</span>
                <div>{{ nameOf(row.toMemberId) }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="netAmount" label="净支付金额" min-width="140" />
          </el-table>
        </div>

        <div class="card-panel">
          <div class="side-head">
            <strong>多边轧差</strong>
            <span class="hint">全部义务合并轧差</span>
          </div>
          <div class="side-summary">
            <div class="stat">
              <div class="label">净头寸笔数</div>
              <div class="value">{{ result.multilateral.positionCount }}</div>
            </div>
            <div class="stat">
              <div class="label">Σ|net|</div>
              <div class="value">{{ result.multilateral.grossAmount }}</div>
            </div>
            <div class="stat">
              <div class="label">ΣnetAmount</div>
              <div class="value">{{ result.multilateral.sumNetAmount }}</div>
            </div>
          </div>
          <el-table :data="result.multilateral.positions" stripe>
            <el-table-column label="会员" min-width="180">
              <template #default="{ row }">
                <span class="mono">{{ row.memberId }}</span>
                <div>{{ nameOf(row.memberId) }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="currency" label="币种" width="90" />
            <el-table-column prop="netAmount" label="净头寸（正应收/负应付）" min-width="180" />
          </el-table>
        </div>
      </div>
    </template>

    <div v-else class="card-panel" style="margin-top:16px">
      <el-empty description="选择交割日与币种后运行对比推演" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const settleDate = ref(new Date().toISOString().slice(0, 10))
const currency = ref('USD')
const running = ref(false)
const result = ref(null)
const memberMap = ref({})

function nameOf(id) {
  return memberMap.value[id] || ''
}

async function loadMembers() {
  const { data } = await api.get('/members')
  memberMap.value = Object.fromEntries(data.map((x) => [x.memberId, x.name]))
}

async function compare() {
  running.value = true
  try {
    const { data } = await api.post('/netting-simulations/compare', {
      settleDate: settleDate.value,
      currency: currency.value
    })
    result.value = data
    ElMessage.success('推演完成，义务保持 OPEN')
  } catch (e) {
    result.value = null
  } finally {
    running.value = false
  }
}

onMounted(loadMembers)
</script>

<style scoped>
.compare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.side-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.side-summary {
  display: flex;
  gap: 32px;
  margin: 12px 0;
}
.stat .label {
  color: var(--muted);
  font-size: 13px;
}
.stat .value {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
}
.hint {
  color: var(--muted);
  font-size: 13px;
}
@media (max-width: 1100px) {
  .compare-grid {
    grid-template-columns: 1fr;
  }
}
</style>
