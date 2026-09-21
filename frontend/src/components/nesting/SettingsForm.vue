<script setup lang="ts">
/**
 * Form tham số in.
 *
 * Người dùng nhập bằng CENTIMÉT, store giữ bằng MILIMÉT. Mỗi trường có một cặp
 * computed get/set làm cầu nối, nhờ vậy không có chỗ nào trong app phải nhớ "số này đang
 * là đơn vị gì".
 *
 * Lỗi validate hiện ngay dưới ô nhập, không dùng alert.
 */
import { computed } from 'vue'
import AppCard from '@/components/ui/AppCard.vue'
import AppNumberInput from '@/components/ui/AppNumberInput.vue'
import AppToggle from '@/components/ui/AppToggle.vue'
import { cmToMm, mmToCm } from '@/api/units'
import type { NestSettings } from '@/types'

const settings = defineModel<NestSettings>({ required: true })

/** Cầu nối cm ↔ mm cho một trường bắt buộc. */
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

/**
 * Ba cách xếp, từ an toàn nhất tới tiết kiệm nhất.
 *
 * <p>Xếp theo thứ tự này có chủ ý: mặc định là lựa chọn an toàn cho mọi kiểu cắt, càng
 * xuống dưới càng tiết kiệm nhưng càng đòi hỏi cách cắt phù hợp. Người dùng phải đọc và
 * chọn chứ không được mặc định vào chế độ có thể làm hỏng bản in.
 */
const MODES: Array<{ value: NestSettings['mode']; title: string; hint: string }> = [
  {
    value: 'ORTHOGONAL',
    title: 'Khung chữ nhật (mặc định)',
    hint: 'Mỗi hình chiếm trọn khung chữ nhật, chỉ xoay 0 hoặc 90 độ. Bố trí thành lưới ngay ngắn, cắt kiểu nào cũng được.',
  },
  {
    value: 'FREE',
    title: 'Nhồi hình nhỏ vào góc trống',
    hint: 'Hình nhỏ được chui LỌT HẲN vào phần trống bên trong khung của hình lớn. Chỉ dùng được khi cắt theo viền hình.',
  },
  {
    value: 'TRUE_SHAPE',
    title: 'Xếp lồng theo hình thật',
    hint: 'Các hình lồng vào nhau theo đường nét thật, xoay nhiều góc kể cả góc chéo. CHỈ dùng khi cắt theo viền hình — cắt theo hình chữ nhật sẽ hỏng bản in.',
  },
]

/**
 * Công tắc xoay chặn NHỮNG GÓC NÀO thì tuỳ chế độ đang chọn, nên lời nhắc phải đổi theo.
 *
 * <p>Trước đây nhãn ghi cứng là "xoay 90 độ". Ở chế độ xếp lồng theo hình thật điều đó
 * SAI: tắt công tắc sẽ chặn cả 8 góc, kể cả 45 độ. Người dùng tắt vì tưởng chỉ bỏ góc 90
 * sẽ mất gần hết phần tiết kiệm mà không hiểu vì sao.
 */
const rotateHint = computed(() =>
  settings.value.mode === 'TRUE_SHAPE'
    ? 'Tắt sẽ chặn TẤT CẢ các góc xoay, kể cả 45 độ — hình giữ nguyên chiều như trong file. '
      + 'Chỉ tắt khi hình buộc phải đúng chiều (chữ phải đọc ngang, canh sợi vải).'
    : 'Tắt sẽ chặn xoay 90 độ ở mọi hình, ghi đè lên cột “Cho xoay” ở bước trước. '
      + 'Xoay được thường tiết kiệm thêm rất nhiều.',
)

/** Chiều dài tối đa: để trống nghĩa là không giới hạn, nên giữ được null. */
const maxLengthCm = computed<number | null>({
  get: () =>
    settings.value.maxSheetLengthMm === null ? null : mmToCm(settings.value.maxSheetLengthMm),
  set: (value) => {
    settings.value.maxSheetLengthMm = value === null || value <= 0 ? null : cmToMm(value)
  },
})

// --- Kiểm tra hợp lệ ---

const sheetWidthError = computed(() => {
  if (!sheetWidthCm.value || sheetWidthCm.value <= 0) return 'Khổ ngang phải lớn hơn 0.'
  if (sheetWidthCm.value > 1000) return 'Khổ ngang lớn bất thường, hãy kiểm tra lại đơn vị.'
  return ''
})

const marginError = computed(() => {
  if (marginCm.value === null || marginCm.value < 0) return 'Lề biên không được âm.'
  if (sheetWidthCm.value && marginCm.value * 2 >= sheetWidthCm.value) {
    return 'Lề biên quá lớn so với khổ ngang.'
  }
  return ''
})

const gapError = computed(() => {
  if (gapCm.value === null || gapCm.value < 0) return 'Khoảng cách không được âm.'
  return ''
})

const maxLengthError = computed(() => {
  if (maxLengthCm.value !== null && maxLengthCm.value <= 0) {
    return 'Chiều dài tối đa phải lớn hơn 0, hoặc để trống để không giới hạn.'
  }
  return ''
})

/** Form hợp lệ thì màn cha mới cho bấm nút ghép. */
const isValid = computed(
  () => !sheetWidthError.value && !marginError.value && !gapError.value && !maxLengthError.value,
)

defineExpose({ isValid })
</script>

<template>
  <div class="stack gap-4">
    <AppCard title="Khổ in" subtitle="Kích thước cuộn giấy và giới hạn chiều dài mỗi file.">
      <div class="fields">
        <AppNumberInput
          v-model="sheetWidthCm"
          label="Khổ ngang"
          suffix="cm"
          :min="1"
          :step="0.5"
          hint="Chiều rộng cuộn giấy, đo thực tế không kể phần mép kẹp."
          :error="sheetWidthError"
        />
        <AppNumberInput
          v-model="marginCm"
          label="Lề biên"
          suffix="cm"
          :min="0"
          :step="0.1"
          hint="Chừa trống mỗi phía để máy in không ăn vào hình."
          :error="marginError"
        />
        <AppNumberInput
          v-model="maxLengthCm"
          label="Chiều dài tối đa mỗi file"
          suffix="cm"
          :min="0"
          :step="10"
          allow-empty
          placeholder="Để trống = không giới hạn"
          hint="Máy in yếu dễ bị lag với file quá dài; app sẽ tự chia thành nhiều file."
          :error="maxLengthError"
        />
      </div>
    </AppCard>

    <AppCard title="Khoảng cách & xoay" subtitle="Ảnh hưởng trực tiếp tới số mét giấy tốn.">
      <div class="fields">
        <AppNumberInput
          v-model="gapCm"
          label="Khoảng cách giữa các hình"
          suffix="cm"
          :min="0"
          :step="0.1"
          hint="Khoảng hở tối thiểu để thợ có đường cắt."
          :error="gapError"
        />
      </div>

      <div class="switches">
        <fieldset class="modes">
          <legend class="modes__legend">Cách xếp</legend>
          <label v-for="option in MODES" :key="option.value" class="mode">
            <input
              v-model="settings.mode"
              type="radio"
              name="nesting-mode"
              :value="option.value"
              class="mode__radio"
            />
            <span class="mode__body">
              <span class="mode__title">{{ option.title }}</span>
              <span class="mode__hint">{{ option.hint }}</span>
            </span>
          </label>
        </fieldset>
        <AppToggle
          v-model="settings.allowRotateGlobal"
          label="Cho phép xoay hình"
          :hint="rotateHint"
        />
        <AppToggle
          v-model="settings.drawCutLines"
          label="Vẽ đường cắt quanh mỗi hình"
          hint="Đường xám mờ 0,25pt, giúp thợ cắt nhanh hơn."
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

.modes {
  border: 0;
  margin: 0 0 var(--s-4);
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--s-2);
}

.modes__legend {
  padding: 0 0 var(--s-2);
  font-size: var(--fs-sm);
  font-weight: 550;
  color: var(--c-text);
}

.mode {
  display: flex;
  gap: var(--s-3);
  padding: var(--s-3);
  border: 1px solid var(--c-border);
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: border-color var(--t-fast), background var(--t-fast);
}

.mode:hover {
  border-color: var(--c-border-strong);
}

/* Viền và nền nhạt để đánh dấu lựa chọn — không dùng đổ bóng. */
.mode:has(.mode__radio:checked) {
  border-color: var(--c-accent);
  background: var(--c-accent-soft);
}

.mode__radio {
  margin: 2px 0 0;
  accent-color: var(--c-accent);
  flex-shrink: 0;
}

.mode__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mode__title {
  font-size: var(--fs-sm);
  font-weight: 550;
}

.mode__hint {
  font-size: var(--fs-xs);
  color: var(--c-text-mute);
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
