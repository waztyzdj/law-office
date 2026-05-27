<script setup lang="ts">
import type {
  CurrentUserOrganization,
  CurrentUserProfile,
  CurrentUserTenant,
} from '#/api/system/user';

import { computed, onMounted, reactive, ref, watch } from 'vue';

import { useUserStore } from '@vben/stores';

import { CameraOutlined } from '@ant-design/icons-vue';
import {
  Avatar,
  Button,
  Card,
  Descriptions,
  DescriptionsItem,
  Form,
  FormItem,
  Input,
  Modal,
  Space,
  Spin,
  Tabs,
  TabPane,
  Tag,
  Tree,
  Upload,
  message,
} from 'ant-design-vue';

import { BaseTable } from '#/components/BaseTable';
import { buildAntTreeData, collectTreeKeys } from '#/composables/Tree/useTree';
import {
  changeCurrentUserPassword,
  getCurrentUserOrganization,
  getCurrentUserProfile,
  getCurrentUserTenantOptions,
  updateCurrentUserProfile,
  uploadCurrentUserAvatar,
} from '#/api/system/user';
import { useAuthStore } from '#/store';

import { getProfileLogColumns } from './hooks/useProfileLogColumns';
import { useProfileLogTable } from './hooks/useProfileLogTable';

const userStore = useUserStore();
const authStore = useAuthStore();

const activeTab = ref('basic');
const loading = ref(false);
const savingProfile = ref(false);
const savingPassword = ref(false);
const switchingTenantId = ref<string>();
const uploadingAvatar = ref(false);
const avatarLoadFailed = ref(false);
const profile = ref<CurrentUserProfile>({});
const organization = ref<CurrentUserOrganization>({
  departRoles: [],
  departs: [],
  menuPermissionCount: 0,
  menuPermissions: [],
  roles: [],
});
const tenants = ref<CurrentUserTenant[]>([]);
const permissionExpandedKeys = ref<Array<number | string>>([]);

const profileForm = reactive<CurrentUserProfile>({});
const passwordForm = reactive({
  confirmPassword: '',
  newPassword: '',
  oldPassword: '',
});
const passwordRules = [
  {
    label: '8-20 位',
    valid: computed(() => /^.{8,20}$/.test(passwordForm.newPassword)),
  },
  {
    label: '包含大写字母',
    valid: computed(() => /[A-Z]/.test(passwordForm.newPassword)),
  },
  {
    label: '包含小写字母',
    valid: computed(() => /[a-z]/.test(passwordForm.newPassword)),
  },
  {
    label: '包含数字',
    valid: computed(() => /\d/.test(passwordForm.newPassword)),
  },
  {
    label: '包含特殊字符',
    valid: computed(() => /[^\dA-Za-z]/.test(passwordForm.newPassword)),
  },
];
const isPasswordStrong = computed(() =>
  passwordRules.every((rule) => rule.valid.value),
);

const displayName = computed(
  () => profile.value.realname || profile.value.username || userStore.userInfo?.realName || '-',
);
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase());
const statusText = computed(() => (profile.value.status === 1 ? '正常' : '冻结'));
const permissionTreeData = computed(() =>
  buildAntTreeData(organization.value.menuPermissions),
);
const {
  activeFilters: logActiveFilters,
  dataSource: logDataSource,
  handleTableChange,
  loadData: loadLogData,
  loading: logLoading,
  pagination: logPagination,
} = useProfileLogTable();
const handleLogTableChange = (pag: any, filters: any, sorter: any) => {
  handleTableChange(pag, filters, sorter);
};
const logTableConfig = computed(() =>
  getProfileLogColumns(logActiveFilters, handleLogTableChange, logPagination),
);

function syncProfileForm(data: CurrentUserProfile) {
  avatarLoadFailed.value = false;
  Object.assign(profileForm, {
    email: data.email,
    phone: data.phone,
    post: data.post,
    realname: data.realname,
    telephone: data.telephone,
  });
}

async function loadData() {
  loading.value = true;
  try {
    const [profileData, organizationData, tenantData] = await Promise.all([
      getCurrentUserProfile(),
      getCurrentUserOrganization(),
      getCurrentUserTenantOptions(),
    ]);
    profile.value = profileData;
    organization.value = organizationData;
    tenants.value = tenantData;
    syncProfileForm(profileData);
  } finally {
    loading.value = false;
  }
}

async function handleSaveProfile() {
  savingProfile.value = true;
  try {
    profile.value = await updateCurrentUserProfile(profileForm);
    syncProfileForm(profile.value);
    await authStore.fetchUserInfo();
    message.success('个人资料已保存');
  } finally {
    savingProfile.value = false;
  }
}

async function handleAvatarUpload(file: File) {
  uploadingAvatar.value = true;
  try {
    profile.value = await uploadCurrentUserAvatar(file);
    syncProfileForm(profile.value);
    await authStore.fetchUserInfo();
    message.success('头像已更新');
  } finally {
    uploadingAvatar.value = false;
  }
  return false;
}

function handleAvatarError() {
  avatarLoadFailed.value = true;
  message.warning('头像已上传，但当前头像地址暂时无法访问');
  return false;
}

async function handleChangePassword() {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    message.warning('请输入旧密码和新密码');
    return;
  }
  if (!isPasswordStrong.value) {
    message.warning('新密码需为8-20位，包含大小写字母、数字和特殊字符');
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.warning('两次输入的新密码不一致');
    return;
  }

  savingPassword.value = true;
  try {
    await changeCurrentUserPassword(passwordForm);
    Object.assign(passwordForm, {
      confirmPassword: '',
      newPassword: '',
      oldPassword: '',
    });
    message.success('密码修改成功，请使用新密码重新登录');
    await authStore.logout();
  } finally {
    savingPassword.value = false;
  }
}

function handleSwitchTenant(tenant: CurrentUserTenant) {
  if (!tenant.id || tenant.current) {
    return;
  }

  Modal.confirm({
    title: '确认切换租户',
    content: `切换到"${tenant.name || ''}"后页面将刷新，是否继续？`,
    async onOk() {
      switchingTenantId.value = tenant.id;
      try {
        await authStore.changeTenant(tenant.id || '');
      } finally {
        switchingTenantId.value = undefined;
      }
    },
  });
}

function formatDateRange(begin?: string, end?: string) {
  if (!begin && !end) {
    return '长期有效';
  }
  return `${begin || '未设置'} 至 ${end || '未设置'}`;
}

watch(
  () => organization.value.menuPermissions,
  (permissions) => {
    permissionExpandedKeys.value = collectTreeKeys(permissions);
  },
);

onMounted(() => {
  loadData();
  loadLogData();
});
</script>

<template>
  <div class="profile-page">
    <Spin :spinning="loading">
      <div class="profile-layout">
        <aside class="profile-summary">
          <Avatar :src="avatarLoadFailed ? undefined : profile.avatar" :size="72" @error="handleAvatarError">
            {{ avatarText }}
          </Avatar>
          <div class="summary-name">{{ displayName }}</div>
          <div class="summary-sub">{{ profile.username || '-' }}</div>
          <Tag :color="profile.status === 1 ? 'green' : 'red'">
            {{ statusText }}
          </Tag>
          <Descriptions class="summary-desc" :column="1" size="small">
            <DescriptionsItem label="当前租户">
              {{ profile.tenantName || profile.tenantId || '-' }}
            </DescriptionsItem>
            <DescriptionsItem label="邮箱">
              {{ profile.email || '-' }}
            </DescriptionsItem>
            <DescriptionsItem label="手机">
              {{ profile.phone || '-' }}
            </DescriptionsItem>
          </Descriptions>
        </aside>

        <Card class="profile-main" :bordered="false">
          <Tabs v-model:active-key="activeTab">
            <TabPane key="basic" tab="基本资料">
              <Form
                class="profile-form"
                :label-col="{ style: { width: '80px' } }"
                :model="profileForm"
                :wrapper-col="{ flex: '1' }"
              >
                <FormItem label="头像">
                  <div class="avatar-uploader-row">
                    <Avatar :src="avatarLoadFailed ? undefined : profile.avatar" :size="72" @error="handleAvatarError">
                      {{ avatarText }}
                    </Avatar>
                    <Upload
                      accept="image/*"
                      :before-upload="handleAvatarUpload"
                      :disabled="uploadingAvatar"
                      :show-upload-list="false"
                    >
                      <Button :loading="uploadingAvatar">
                        <CameraOutlined />
                        上传头像
                      </Button>
                    </Upload>
                    <span class="muted-text">支持图片格式，建议不超过 2MB</span>
                  </div>
                </FormItem>
                <FormItem label="登录账号">
                  <Input :value="profile.username" disabled />
                </FormItem>
                <FormItem label="真实姓名">
                  <Input v-model:value="profileForm.realname" :maxlength="64" />
                </FormItem>
                <FormItem label="手机号">
                  <Input v-model:value="profileForm.phone" :maxlength="45" />
                </FormItem>
                <FormItem label="邮箱">
                  <Input v-model:value="profileForm.email" :maxlength="45" />
                </FormItem>
                <FormItem label="座机号">
                  <Input v-model:value="profileForm.telephone" :maxlength="45" />
                </FormItem>
                <FormItem label="职务">
                  <Input v-model:value="profileForm.post" :maxlength="64" />
                </FormItem>
                <Button type="primary" :loading="savingProfile" @click="handleSaveProfile">
                  保存资料
                </Button>
              </Form>
            </TabPane>

            <TabPane key="security" tab="账号安全">
              <section class="profile-section">
                <h3>修改密码</h3>
                <Form class="password-form" :model="passwordForm" layout="vertical">
                  <FormItem label="旧密码" required>
                    <Input.Password v-model:value="passwordForm.oldPassword" />
                  </FormItem>
                  <FormItem label="新密码" required>
                    <Input.Password v-model:value="passwordForm.newPassword" />
                    <div class="password-rule-list">
                      <Tag
                        v-for="rule in passwordRules"
                        :key="rule.label"
                        :color="rule.valid.value ? 'green' : 'default'"
                      >
                        {{ rule.label }}
                      </Tag>
                    </div>
                  </FormItem>
                  <FormItem label="确认新密码" required>
                    <Input.Password v-model:value="passwordForm.confirmPassword" />
                  </FormItem>
                  <Button
                    type="primary"
                    :loading="savingPassword"
                    @click="handleChangePassword"
                  >
                    修改密码
                  </Button>
                </Form>
              </section>
            </TabPane>

            <TabPane key="organization" tab="组织权限">
              <div class="organization-panel">
                <section class="organization-card">
                  <div class="organization-card-head">
                    <h3>所属部门</h3>
                    <span>{{ organization.departs.length }}</span>
                  </div>
                  <Space v-if="organization.departs.length > 0" class="organization-tags" wrap>
                    <Tag v-for="depart in organization.departs" :key="depart.id" color="blue">
                      {{ depart.departName || depart.orgCode }}
                    </Tag>
                  </Space>
                  <div v-else class="empty-text">暂无所属部门</div>
                </section>
                <section class="organization-card">
                  <div class="organization-card-head">
                    <h3>系统角色</h3>
                    <span>{{ organization.roles.length }}</span>
                  </div>
                  <Space v-if="organization.roles.length > 0" class="organization-tags" wrap>
                    <Tag v-for="role in organization.roles" :key="role.id" color="green">
                      {{ role.roleName || role.roleCode }}
                    </Tag>
                  </Space>
                  <div v-else class="empty-text">暂无系统角色</div>
                </section>
                <section class="organization-card">
                  <div class="organization-card-head">
                    <h3>部门角色</h3>
                    <span>{{ organization.departRoles.length }}</span>
                  </div>
                  <Space v-if="organization.departRoles.length > 0" class="organization-tags" wrap>
                    <Tag v-for="role in organization.departRoles" :key="role.id" color="cyan">
                      {{ role.roleName || role.roleCode }}
                    </Tag>
                  </Space>
                  <div v-else class="empty-text">暂无部门角色</div>
                </section>
              </div>
              <section class="permission-panel">
                <div class="permission-panel-head">
                  <h3>菜单权限</h3>
                  <span>
                    已授权
                    <strong>{{ organization.menuPermissionCount || 0 }}</strong>
                    项
                  </span>
                </div>
                <div class="permission-tree-wrap">
                  <Tree
                    v-if="permissionTreeData.length > 0"
                    :expanded-keys="permissionExpandedKeys"
                    :selectable="false"
                    show-line
                    :tree-data="permissionTreeData"
                    @expand="(keys) => (permissionExpandedKeys = keys)"
                  />
                  <div v-else class="empty-text">暂无菜单权限</div>
                </div>
              </section>
            </TabPane>

            <TabPane key="tenants" tab="我的租户">
              <div class="tenant-list">
                <div v-for="tenant in tenants" :key="tenant.id" class="tenant-item">
                  <div class="tenant-main">
                    <div class="tenant-name">
                      {{ tenant.name || tenant.id }}
                      <Tag v-if="tenant.current" color="blue">当前</Tag>
                      <Tag :color="tenant.status === 1 ? 'green' : 'red'">
                        {{ tenant.status === 1 ? '正常' : '冻结' }}
                      </Tag>
                    </div>
                    <div class="tenant-period">
                      <span class="period-label">有效期</span>
                      <span>{{ formatDateRange(tenant.beginDate, tenant.endDate) }}</span>
                    </div>
                  </div>
                  <Button
                    type="primary"
                    :disabled="tenant.current || tenant.status !== 1"
                    :loading="switchingTenantId === tenant.id"
                    @click="handleSwitchTenant(tenant)"
                  >
                    切换
                  </Button>
                </div>
                <div v-if="tenants.length === 0" class="empty-text">暂无可切换租户</div>
              </div>
            </TabPane>

            <TabPane key="logs" tab="操作日志">
              <BaseTable
                :columns="logTableConfig.columns"
                :data-source="logDataSource"
                :loading="logLoading"
                :pagination="logPagination"
                :scroll="logTableConfig.scroll"
                row-key="id"
                :show-card="false"
                :show-toolbar="false"
                size="small"
                @change="handleLogTableChange"
              />
            </TabPane>
          </Tabs>
        </Card>
      </div>
    </Spin>
  </div>
</template>

<style scoped>
.profile-page {
  padding: 16px;
}

.profile-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}

.profile-summary,
.profile-main {
  border-radius: 8px;
  background: hsl(var(--background));
}

.profile-summary {
  display: flex;
  min-height: 360px;
  flex-direction: column;
  align-items: center;
  padding: 28px 20px;
}

.summary-name {
  margin-top: 14px;
  font-size: 18px;
  font-weight: 600;
}

.summary-sub,
.muted-text,
.tenant-period,
.empty-text {
  color: hsl(var(--muted-foreground));
}

.summary-desc {
  width: 100%;
  margin-top: 20px;
}

.profile-form,
.password-form {
  max-width: 560px;
}

.profile-form :deep(.ant-form-item) {
  margin-bottom: 14px;
}

.profile-form :deep(.ant-form-item-label) {
  padding-bottom: 0;
}

.profile-form :deep(.ant-form-item-control-input) {
  min-height: 34px;
}

.profile-form :deep(.ant-input),
.profile-form :deep(.ant-input-affix-wrapper) {
  height: 34px;
}

.avatar-uploader-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.avatar-uploader-row :deep(.ant-avatar) {
  width: 56px !important;
  height: 56px !important;
  line-height: 56px !important;
}

.password-rule-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.password-rule-list :deep(.ant-tag) {
  margin-inline-end: 0;
}

.profile-section h3,
.organization-card h3,
.permission-panel h3 {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
}

.organization-card h3,
.permission-panel h3 {
  margin-bottom: 0;
}

.profile-section + .profile-section {
  margin-top: 24px;
}

.organization-panel {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.organization-card,
.permission-panel {
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
  background: hsl(var(--background));
}

.organization-card {
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
}

.organization-card-head,
.permission-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.organization-card-head span,
.permission-panel-head span {
  color: hsl(var(--muted-foreground));
  font-size: 13px;
  white-space: nowrap;
}

.organization-tags {
  max-height: 56px;
  margin-top: 10px;
  overflow: auto;
}

.permission-panel {
  margin-top: 14px;
}

.permission-panel-head {
  padding: 14px 16px 12px;
  border-bottom: 1px solid hsl(var(--border));
}

.permission-panel-head strong {
  margin: 0 4px;
  color: hsl(var(--primary));
}

.permission-tree-wrap {
  max-height: 360px;
  min-height: 180px;
  overflow: auto;
  padding: 12px 16px;
}

.tenant-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tenant-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid hsl(var(--border));
  border-radius: 8px;
}

.tenant-main {
  min-width: 0;
}

.tenant-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.tenant-period {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
}

.period-label {
  color: hsl(var(--foreground));
  font-weight: 500;
}

.empty-text {
  padding: 8px 0;
}

@media (max-width: 960px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .organization-panel {
    grid-template-columns: 1fr;
  }
}
</style>
