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

import { SearchOutlined } from '@vicons/antd'
import { NButton, NDatePicker, NSelect, NIcon, NSpace } from 'naive-ui'
import { defineComponent, getCurrentInstance, PropType } from 'vue'
import { useI18n } from 'vue-i18n'
import { format } from 'date-fns'
import SearchInput from '@/components/search-input'
import { workflowExecutionStateType } from '@/common/common'

export default defineComponent({
  name: 'ProcessInstanceCondition',
  props: {
    searchVal: {
      type: String,
      default: ''
    },
    executorName: {
      type: String,
      default: ''
    },
    host: {
      type: String,
      default: ''
    },
    stateType: {
      type: String,
      default: ''
    },
    startEndTime: {
      type: Array as unknown as PropType<[number, number] | null>,
      default: null
    }
  },
  emits: [
    'update:searchVal',
    'update:executorName',
    'update:host',
    'update:stateType',
    'update:startEndTime',
    'handleSearch'
  ],
  setup(props, ctx) {
    const handleSearch = () => {
      let startDate = ''
      let endDate = ''
      if (props.startEndTime) {
        startDate = format(
          new Date(props.startEndTime[0]),
          'yyyy-MM-dd HH:mm:ss'
        )
        endDate = format(
          new Date(props.startEndTime[1]),
          'yyyy-MM-dd HH:mm:ss'
        )
      }

      ctx.emit('handleSearch', {
        searchVal: props.searchVal,
        executorName: props.executorName,
        host: props.host,
        stateType: props.stateType,
        startDate,
        endDate
      })
    }

    const onClearSearchVal = () => {
      ctx.emit('update:searchVal', '')
      handleSearch()
    }

    const onClearSearchHost = () => {
      ctx.emit('update:host', '')
      handleSearch()
    }

    const onClearSearchExecutor = () => {
      ctx.emit('update:executorName', '')
      handleSearch()
    }

    const trim = getCurrentInstance()?.appContext.config.globalProperties.trim

    return {
      handleSearch,
      onClearSearchVal,
      onClearSearchExecutor,
      onClearSearchHost,
      trim
    }
  },
  render() {
    const { t } = useI18n()
    const options = workflowExecutionStateType(t)

    return (
      <NSpace justify='end'>
        <SearchInput
          onSearch={this.handleSearch}
          allowInput={this.trim}
          size='small'
          value={this.searchVal}
          onUpdateValue={(value: string) =>
            this.$emit('update:searchVal', value)
          }
          placeholder={t('project.workflow.name')}
          clearable
          onClear={this.onClearSearchVal}
        />
        <SearchInput
          onSearch={this.handleSearch}
          allowInput={this.trim}
          size='small'
          value={this.executorName}
          onUpdateValue={(value: string) =>
            this.$emit('update:executorName', value)
          }
          placeholder={t('project.workflow.executor')}
          clearable
          onClear={this.onClearSearchExecutor}
        />
        <SearchInput
          onSearch={this.handleSearch}
          allowInput={this.trim}
          size='small'
          value={this.host}
          onUpdateValue={(value: string) => this.$emit('update:host', value)}
          placeholder={t('project.workflow.host')}
          clearable
          onClear={this.onClearSearchHost}
        />
        <NSelect
          options={options}
          size='small'
          style={{ width: '210px' }}
          defaultValue={''}
          value={this.stateType}
          onUpdateValue={(value: string) =>
            this.$emit('update:stateType', value)
          }
        />
        <NDatePicker
          type='datetimerange'
          size='small'
          clearable
          value={this.startEndTime}
          onUpdateValue={(value: [number, number] | null) =>
            this.$emit('update:startEndTime', value)
          }
        />
        <NButton type='primary' size='small' onClick={this.handleSearch}>
          <NIcon>
            <SearchOutlined />
          </NIcon>
        </NButton>
      </NSpace>
    )
  }
})
