<script setup lang="ts">
/** Ô nhập chữ. Luôn có label gắn `for` để bấm vào label là focus được vào ô. */
import { useId } from '@/composables/useId'

defineProps<{
  label: string
  hint?: string
  error?: string
  suffix?: string
  placeholder?: string
  disabled?: boolean
}>()

const model = defineModel<string>({ default: '' })
const id = useId('input')
</script>

<template>
  <div class="field">
    <label class="field__label" :for="id">{{ label }}</label>
    <div :class="['field__box', { 'field__box--error': error }]">
      <input
        :id="id"
        v-model="model"
        class="field__input"
        type="text"
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
