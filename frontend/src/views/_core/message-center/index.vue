<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';

import { Inbox, MessageSquareCode } from '@vben/icons';

import { Card, TabPane, Tabs } from 'ant-design-vue';

import MessageInboxPage from '#/views/message/inbox/index.vue';
import MessageSentPage from '#/views/message/sent/index.vue';

const activeTab = ref<'inbox' | 'sent'>('inbox');
const route = useRoute();
const inboxPageRef = ref<InstanceType<typeof MessageInboxPage>>();
const sentPageRef = ref<InstanceType<typeof MessageSentPage>>();

function getQueryValue(value: unknown) {
  return Array.isArray(value) ? value[0] : value;
}

async function openDetailFromRoute() {
  const detailId = getQueryValue(route.query.detailId);
  if (typeof detailId !== 'string' || !detailId) {
    return;
  }

  const tab = getQueryValue(route.query.tab) === 'sent' ? 'sent' : 'inbox';
  activeTab.value = tab;
  await nextTick();
  await nextTick();

  if (tab === 'sent') {
    sentPageRef.value?.openDetail(detailId);
    return;
  }
  inboxPageRef.value?.openDetail(detailId);
}

onMounted(() => {
  void openDetailFromRoute();
});

watch(
  [() => route.query.detailId, () => route.query.tab],
  () => {
    void openDetailFromRoute();
  },
);
</script>

<template>
  <div class="message-center-page">
    <Card class="message-center-card" :bordered="false">
      <div class="message-center-head">
        <h2>消息中心</h2>
      </div>

      <Tabs v-model:active-key="activeTab" class="message-center-tabs">
        <TabPane key="inbox">
          <template #tab>
            <span class="message-center-tab">
              <Inbox class="size-4" />
              收件箱
            </span>
          </template>
          <MessageInboxPage ref="inboxPageRef" embedded />
        </TabPane>
        <TabPane key="sent" force-render>
          <template #tab>
            <span class="message-center-tab">
              <MessageSquareCode class="size-4" />
              发件箱
            </span>
          </template>
          <MessageSentPage ref="sentPageRef" embedded />
        </TabPane>
      </Tabs>
    </Card>
  </div>
</template>

<style scoped>
.message-center-page {
  padding: 16px;
}

.message-center-card {
  border-radius: 8px;
}

.message-center-card :deep(.ant-card-body) {
  padding: 16px;
}

.message-center-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 12px;
}

.message-center-head h2 {
  margin: 0;
  color: hsl(var(--foreground));
  font-size: 18px;
  font-weight: 600;
  line-height: 28px;
}

.message-center-tabs {
  :deep(.ant-tabs-nav) {
    margin: 0 0 12px;
  }

  :deep(.ant-tabs-content-holder) {
    min-width: 0;
  }
}

.message-center-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

@media (max-width: 640px) {
  .message-center-head {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
