<script setup lang="ts">
import { 
  Card, 
  Row, 
  Col, 
  FormItem, 
  Input, 
  Select, 
  Button, 
  Space 
} from 'ant-design-vue';
import type { SearchFormState } from '../composables/useUserSearch';

interface Props {
  searchForm: SearchFormState;
}

interface Emits {
  (e: 'search'): void;
  (e: 'reset'): void;
}

defineProps<Props>();
const emit = defineEmits<Emits>();
</script>

<template>
  <Card class="search-card" title="搜索条件">
    <Row :gutter="16">
      <Col :span="6">
        <FormItem label="用户名">
          <Input 
            v-model:value="searchForm.username" 
            placeholder="请输入用户名" 
            allow-clear
          />
        </FormItem>
      </Col>
      <Col :span="6">
        <FormItem label="真实姓名">
          <Input 
            v-model:value="searchForm.realname" 
            placeholder="请输入真实姓名" 
            allow-clear
          />
        </FormItem>
      </Col>
      <Col :span="6">
        <FormItem label="电话">
          <Input 
            v-model:value="searchForm.phone" 
            placeholder="请输入电话" 
            allow-clear
          />
        </FormItem>
      </Col>
      <Col :span="6">
        <FormItem label="状态">
          <Select 
            v-model:value="searchForm.status" 
            placeholder="请选择状态" 
            allow-clear
          >
            <Select.Option :value="1">正常</Select.Option>
            <Select.Option :value="2">冻结</Select.Option>
          </Select>
        </FormItem>
      </Col>
    </Row>
    <Row>
      <Col :span="24" style="text-align: right;">
        <Space>
          <Button @click="$emit('reset')">重置</Button>
          <Button type="primary" @click="$emit('search')">搜索</Button>
        </Space>
      </Col>
    </Row>
  </Card>
</template>

<style scoped>
.search-card {
  margin-bottom: 16px;
}

:deep(.ant-form-item) {
  margin-bottom: 16px;
}
</style>
