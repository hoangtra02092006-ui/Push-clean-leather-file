/**
 * Sinh id duy nhat cho cap label/input.
 *
 * Vue 3.5 co san `useId()`, nhung ta tu viet de khong rang buoc vao phien ban toi thieu,
 * va de id doc duoc khi kiem tra DOM (`printnest-number-3` de hieu hon `v-0-1`).
 */
import { getCurrentInstance } from 'vue'

let counter = 0

export function useId(prefix = 'field'): string {
  counter += 1
  const scope = getCurrentInstance()?.type.__name ?? 'printnest'
  return `${scope}-${prefix}-${counter}`
}
