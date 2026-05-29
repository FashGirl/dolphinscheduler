/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useRoute } from 'vue-router'

const STORAGE_PREFIX = 'ds-list-search:'

export function getListSearchStorageKey(routePath: string) {
  return `${STORAGE_PREFIX}${routePath}`
}

export function restoreListSearchState<T extends Record<string, unknown>>(
  storageKey: string,
  state: T,
  fields: (keyof T)[]
): boolean {
  try {
    const raw = sessionStorage.getItem(storageKey)
    if (!raw) {
      return false
    }
    const saved = JSON.parse(raw) as Record<string, unknown>
    fields.forEach((field) => {
      if (Object.prototype.hasOwnProperty.call(saved, field as string)) {
        state[field] = saved[field as string] as T[keyof T]
      }
    })
    return true
  } catch {
    return false
  }
}

export function persistListSearchState<T extends Record<string, unknown>>(
  storageKey: string,
  state: T,
  fields: (keyof T)[]
): void {
  try {
    const payload: Record<string, unknown> = {}
    fields.forEach((field) => {
      payload[field as string] = state[field] ?? null
    })
    sessionStorage.setItem(storageKey, JSON.stringify(payload))
  } catch {
    // ignore quota / private mode errors
  }
}

export function useListSearchState<T extends Record<string, unknown>>(
  state: T,
  fields: (keyof T)[]
) {
  const route = useRoute()
  const storageKey = getListSearchStorageKey(route.path)

  const restore = () => restoreListSearchState(storageKey, state, fields)

  const persist = () => persistListSearchState(storageKey, state, fields)

  restore()

  return {
    storageKey,
    restore,
    persist
  }
}
