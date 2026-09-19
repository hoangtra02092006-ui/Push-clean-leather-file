<script setup lang="ts">
/**
 * O nhap so.
 *
 * Model la `number | null`: `null` tuong ung o de trong, co nghia rieng o vai truong
 * (vi du "chieu dai toi da de trong = khong gioi han"). Neu ep ve 0 thi khong phan biet
 * duoc "de trong" voi "nhap so 0".
 */
import { computed } from 'vue'
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

const text = computed({
  get: () => (model.value === null || Number.isNaN(model.value) ? '' : String(model.value)),
  set: (raw: string) => {
    const trimmed = raw.trim()
    if (trimmed === '') {
      model.value = props.allowEmpty ? null : null
      return
    }
    const parsed = Number(trimmed.replace(',', '.'))
    model.value = Number.isNaN(parsed) ? null : parsed
  },
})
</script>

<template>
  <div class="field">
    <label class="field__label" :for="id">{{ label }}</label>
    <div :class="['field__box', { 'field__box--error': error }]">
      <input
        :id="id"
        v-model="text"
        class="field__input"
        type="number"
        inputmode="decimal"
        :min="min"
        :max="max"
        :step="step"
        :placeholder="placeholder"
        :disabled="disabled"
        :aria-invalid="Boolean(error)"
        :aria-describedby="error ? `${id}-error` : hint ? `${id}-hint` : undefined"
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
