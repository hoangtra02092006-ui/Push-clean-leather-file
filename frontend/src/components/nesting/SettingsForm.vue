<script setup lang="ts">
/**
 * Form tham so in.
 *
 * Nguoi dung nhap bang CENTIMET, store giu bang MILIMET. Moi truong co mot cap
 * computed get/set lam cau noi, nho vay khong co cho nao trong app phai nho "so nay dang
 * la don vi gi".
 *
 * Loi validate hien ngay duoi o nhap, khong dung alert.
 */
import { computed } from 'vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppNumberInput from '@/components/ui/AppNumberInput.vue'
import AppToggle from '@/components/ui/AppToggle.vue'
import { cmToMm, mmToCm } from '@/api/units'
import type { NestSettings } from '@/types'

const settings = defineModel<NestSettings>({ required: true })

/** Cau noi cm <-> mm cho mot truong bat buoc. */
function cmField(key: 'sheetWidthMm' | 'marginMm' | 'gapMm') {
  return computed<number | null>({
    get: () => mmToCm(settings.value[key]),
    set: (value) => {
      settings.value[key] = value === null ? 0 : cmToMm(value)
    },
  })
}

const sheetWidthCm = cmField('sheetWidthMm')
const marginCm = cmField('marginMm')
const gapCm = cmField('gapMm')

/** Chieu dai toi da: de trong nghia la khong gioi han, nen giu duoc null. */
const maxLengthCm = computed<number | null>({
  get: () =>
    settings.value.maxSheetLengthMm === null ? null : mmToCm(settings.value.maxSheetLengthMm),
  set: (value) => {
    settings.value.maxSheetLengthMm = value === null || value <= 0 ? null : cmToMm(value)
  },
})

// --- Validate ---

const sheetWidthError = computed(() => {
  if (!sheetWidthCm.value || sheetWidthCm.value <= 0) return 'Kho ngang phai lon hon 0.'
  if (sheetWidthCm.value > 1000) return 'Kho ngang lon bat thuong, hay kiem tra lai don vi.'
  return ''
})

const marginError = computed(() => {
  if (marginCm.value === null || marginCm.value < 0) return 'Le bien khong duoc am.'
  if (sheetWidthCm.value && marginCm.value * 2 >= sheetWidthCm.value) {
    return 'Le bien qua lon so voi kho ngang.'
  }
  return ''
})

const gapError = computed(() => {
  if (gapCm.value === null || gapCm.value < 0) return 'Khoang cach khong duoc am.'
  return ''
})

const maxLengthError = computed(() => {
  if (maxLengthCm.value !== null && maxLengthCm.value <= 0) {
    return 'Chieu dai toi da phai lon hon 0, hoac de trong de khong gioi han.'
  }
  return ''
})

/** Form hop le thi man cha moi cho bam nut ghep. */
const isValid = computed(
  () => !sheetWidthError.value && !marginError.value && !gapError.value && !maxLengthError.value,
)

defineExpose({ isValid })
</script>

<template>
  <div class="stack gap-4">
    <AppCard title="Kho in" subtitle="Kich thuoc cuon giay va gioi han chieu dai moi file.">
      <div class="fields">
        <AppNumberInput
          v-model="sheetWidthCm"
          label="Kho ngang"
          suffix="cm"
          :min="1"
          :step="0.5"
          hint="Chieu rong cuon giay, do that te khong ke phan mep keo."
          :error="sheetWidthError"
        />
        <AppNumberInput
          v-model="marginCm"
          label="Le bien"
          suffix="cm"
          :min="0"
          :step="0.1"
          hint="Chua trong moi phia de may in khong an vao hinh."
          :error="marginError"
        />
        <AppNumberInput
          v-model="maxLengthCm"
          label="Chieu dai toi da moi file"
          suffix="cm"
          :min="0"
          :step="10"
          allow-empty
          placeholder="De trong = khong gioi han"
          hint="May in yeu de bi lag voi file qua dai; app se tu chia thanh nhieu file."
          :error="maxLengthError"
        />
      </div>
    </AppCard>

    <AppCard title="Khoang cach & xoay" subtitle="Anh huong truc tiep toi so met giay ton.">
      <div class="fields">
        <AppNumberInput
          v-model="gapCm"
          label="Khoang cach giua cac hinh"
          suffix="cm"
          :min="0"
          :step="0.1"
          hint="Khoang ho toi thieu de tho co duong cat."
          :error="gapError"
        />
      </div>

      <div class="switches">
        <AppToggle
          v-model="settings.allowRotateGlobal"
          label="Cho phep xoay 90 do"
          hint="Tat o day se ghi de len tung dong o buoc truoc. Xoay duoc thuong tiet kiem them nhieu."
        />
        <AppToggle
          v-model="settings.drawCutLines"
          label="Ve duong cat quanh moi hinh"
          hint="Duong xam mo 0.25pt, giup tho cat nhanh hon."
        />
      </div>
    </AppCard>
  </div>
</template>

<style scoped>
.fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--s-4);
}

.switches {
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
  margin-top: var(--s-5);
  padding-top: var(--s-4);
  border-top: 1px solid var(--c-border);
}
</style>
