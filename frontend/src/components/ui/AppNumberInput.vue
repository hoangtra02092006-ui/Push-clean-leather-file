<script setup lang="ts">
/**
 * Ô nhập số.
 *
 * Model là `number | null`: `null` tương ứng ô để trống, có nghĩa riêng ở vài trường
 * (ví dụ "chiều dài tối đa để trống = không giới hạn"). Nếu ép về 0 thì không phân biệt
 * được "để trống" với "nhập số 0".
 */
import { computed } from 'vue'
import { parseDecimalInput } from '@/api/units'
import { useId } from '@/composables/useId'

const props = withDefaults(
  defineProps<{
    label: string
    hint?: string
    error?: string
    suffix?: string
    min?: number
    max?: number
    step?: number
    placeholder?: string
    disabled?: boolean
    allowEmpty?: boolean
  }>(),
  { step: 0.1, allowEmpty: false },
)

const model = defineModel<number | null>({ default: null })
const id = useId('number')

/**
 * Cầu nối giữa ô nhập và model số.
 *
 * <p><b>Phải nhận cả chuỗi lẫn số.</b> Với {@code <input type="number">}, Vue tự động ép
 * giá trị thành {@code number} trước khi gọi setter; còn khi ô bị xoá trống thì nó trả về
 * chuỗi rỗng. Trước đây setter gọi thẳng {@code raw.trim()} nên mỗi lần gõ một con số là
 * ném TypeError, và giá trị KHÔNG BAO GIỜ tới được store — cả bốn ô nhập số ở bước 2 nhìn
 * thì đổi được nhưng thực ra luôn giữ nguyên giá trị mặc định.
 */
const text = computed({
  get: () => (model.value === null || Number.isNaN(model.value) ? '' : String(model.value)),
  set: (raw: string | number) => {
    model.value = parseDecimalInput(raw)
  },
})
</script>

<template>
  <div class="field">
    <label class="field__label" :for="id">{{ label }}</label>
    <div :class="['field__box', { 'field__box--error': error }]">
      <!--
        Co y dung type="text" chu khong phai type="number".

        Voi type="number", Vue tu ep gia tri sang so truoc khi trao cho v-model, va trinh
        duyet tu loai bo nhung gi no cho la khong hop le - go "2,5" theo thoi quen Viet Nam
        se bi bien thanh 2 hoac thanh rong, khong cach nao cuu duoc o tang ung dung.

        Doc thang chuoi tho tu DOM thi ta tu quyet dinh cach hieu, nen dau phay va dau cham
        deu dung duoc. inputmode="decimal" van cho ban phim so tren dien thoai; nut tang/giam
        cua trinh duyet von da bi an bang CSS nen giao dien khong doi gi.
      -->
      <input
        :id="id"
        class="field__input"
        type="text"
        inputmode="decimal"
        :value="text"
        :placeholder="placeholder"
        :disabled="disabled"
        :aria-invalid="Boolean(error)"
        :aria-describedby="error ? `${id}-error` : hint ? `${id}-hint` : undefined"
        @input="text = ($event.target as HTMLInputElement).value"
      />
      <span v-if="suffix" class="field__suffix">{{ suffix }}</span>
    </div>
    <p v-if="error" :id="`${id}-error`" class="field__error">{{ error }}</p>
    <p v-else-if="hint" :id="`${id}-hint`" class="field__hint">{{ hint }}</p>
  </div>
</template>

<style scoped>
@import './field.css';
</style>
