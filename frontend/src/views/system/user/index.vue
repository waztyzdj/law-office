<script setup lang="ts">
import { ref, reactive, onMounted, h } from 'vue';
import { 
  Table, 
  Button, 
  Space, 
  Tag, 
  message, 
  Modal, 
  Input, 
  Form, 
  FormItem,
  Select,
  Card,
  Row,
  Col
} from 'ant-design-vue';
import type { ColumnsType } from 'ant-design-vue/es/table';
import { 
  getUserListApi, 
  deleteUserApi, 
  batchDeleteUserApi,
  type UserInfo,
  type UserListParams 
} from '#/api/system/user';
import dayjs from 'dayjs';

// 搜索表单
const searchForm = reactive({
  username: '',
  realname: '',
  phone: '',
  email: '',
  status: undefined as number | undefined,
});

// 表格数据
const dataSource = ref<UserInfo[]>([]);
const loading = ref(false);
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
});

// 选中的行
const selectedRowKeys = ref<string[]>([]);

// 编辑用户
const handleEdit = (record: UserInfo) => {
  message.info(`编辑用户：${record.username}`);
  // TODO: 打开编辑对话框
};

// 删除用户
const handleDelete = (record: UserInfo) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除用户"${record.realname}"吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await deleteUserApi(record.id);
        message.success('删除成功');
        loadData();
      } catch (error) {
        message.error('删除失败');
        console.error(error);
      }
    },
  });
};

// 表格列定义
const columns: ColumnsType<UserInfo> = [
  {
    title: '用户名',
    dataIndex: 'username',
    key: 'username',
    width: 120,
  },
  {
    title: '真实姓名',
    dataIndex: 'realname',
    key: 'realname',
    width: 120,
  },
  {
    title: '性别',
    dataIndex: 'sex',
    key: 'sex',
    width: 80,
    customRender: ({ record }) => {
      const sexMap: Record<number, string> = { 0: '未知', 1: '男', 2: '女' };
      return sexMap[record.sex ?? 0] || '未知';
    },
  },
  {
    title: '邮箱',
    dataIndex: 'email',
    key: 'email',
    width: 180,
  },
  {
    title: '电话',
    dataIndex: 'phone',
    key: 'phone',
    width: 130,
  },
  {
    title: '工号',
    dataIndex: 'workNo',
    key: 'workNo',
    width: 120,
  },
  {
    title: '职务',
    dataIndex: 'post',
    key: 'post',
    width: 120,
  },
  {
    title: '状态',
    dataIndex: 'status',
    key: 'status',
    width: 100,
    customRender: ({ record }) => {
      const statusMap: Record<number, { text: string; color: string }> = {
        1: { text: '正常', color: 'green' },
        2: { text: '冻结', color: 'red' },
      };
      const status = statusMap[record.status ?? 1];
      if (!status) {
        return h(Tag, { color: 'default' }, () => '未知');
      }
      return h(Tag, { color: status.color }, () => status.text);
    },
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
    customRender: ({ record }) => {
      return record.createTime ? dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') : '-';
    },
  },
  {
    title: '操作',
    key: 'action',
    width: 150,
    fixed: 'right',
    customRender: ({ record }) => {
      return h(Space, { size: 'middle' }, {
        default: () => [
          h('a', { onClick: () => handleEdit(record) }, '编辑'),
          h('a', { style: { color: 'red' }, onClick: () => handleDelete(record) }, '删除'),
        ],
      });
    },
  },
];

// 加载数据
const loadData = async () => {
  loading.value = true;
  try {
    const params: UserListParams = {
      current: pagination.current,
      size: pagination.pageSize,
      ...searchForm,
    };
    
    const result = await getUserListApi(params);
    dataSource.value = result.items || [];
    pagination.total = result.total || 0;
  } catch (error) {
    message.error('加载数据失败');
    console.error(error);
  } finally {
    loading.value = false;
  }
};

// 搜索
const handleSearch = () => {
  pagination.current = 1;
  loadData();
};

// 重置
const handleReset = () => {
  searchForm.username = '';
  searchForm.realname = '';
  searchForm.phone = '';
  searchForm.email = '';
  searchForm.status = undefined;
  pagination.current = 1;
  loadData();
};

// 分页变化
const handleTableChange = (pag: any) => {
  pagination.current = pag.current;
  pagination.pageSize = pag.pageSize;
  loadData();
};

// 选择行变化
const onSelectChange = (keys: (string | number)[]) => {
  selectedRowKeys.value = keys.map(key => String(key));
};

// 批量删除
const handleBatchDelete = () => {
  if (selectedRowKeys.value.length === 0) {
    message.warning('请选择要删除的用户');
    return;
  }
  
  Modal.confirm({
    title: '确认批量删除',
    content: `确定要删除选中的 ${selectedRowKeys.value.length} 个用户吗？`,
    okText: '确定',
    cancelText: '取消',
    onOk: async () => {
      try {
        await batchDeleteUserApi(selectedRowKeys.value);
        message.success('批量删除成功');
        selectedRowKeys.value = [];
        loadData();
      } catch (error) {
        message.error('批量删除失败');
        console.error(error);
      }
    },
  });
};

// 新增用户
const handleAdd = () => {
  message.info('新增用户功能待实现');
  // TODO: 打开新增对话框
};

// 初始化加载
onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="user-management">
    <!-- 搜索区域 -->
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
            <Button @click="handleReset">重置</Button>
            <Button type="primary" @click="handleSearch">搜索</Button>
          </Space>
        </Col>
      </Row>
    </Card>

    <!-- 表格区域 -->
    <Card class="table-card" style="margin-top: 16px;">
      <div class="table-toolbar">
        <Space>
          <Button type="primary" @click="handleAdd">新增用户</Button>
          <Button danger @click="handleBatchDelete" :disabled="selectedRowKeys.length === 0">
            批量删除
          </Button>
        </Space>
      </div>
      
      <Table
        :columns="columns"
        :data-source="dataSource"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{
          selectedRowKeys: selectedRowKeys,
          onChange: onSelectChange,
        }"
        row-key="id"
        @change="handleTableChange"
        bordered
      />
    </Card>
  </div>
</template>

<style scoped>
.user-management {
  padding: 16px;
}

.search-card {
  margin-bottom: 16px;
}

.table-toolbar {
  margin-bottom: 16px;
}

:deep(.ant-form-item) {
  margin-bottom: 16px;
}
</style>
