<script lang="ts" setup>
import type { NotificationItem } from './types';

import { watch } from 'vue';

import { Bell, MailCheck } from '@vben/icons';
import { $t } from '@vben/locales';

import {
  VbenButton,
  VbenIconButton,
  VbenPopover,
  VbenScrollbar,
} from '@vben-core/shadcn-ui';

import { useToggle } from '@vueuse/core';

defineOptions({ name: 'NotificationPopup' });

withDefaults(
  defineProps<{
    /** 显示圆点 */
    dot?: boolean;
    /** 消息列表 */
    notifications?: NotificationItem[];
  }>(),
  {
    dot: false,
    notifications: () => [],
  },
);

const emit = defineEmits<{
  clear: [];
  click: [NotificationItem];
  makeAll: [];
  openChange: [boolean];
  read: [NotificationItem];
  select: [NotificationItem];
  viewAll: [];
}>();

const [open, toggle] = useToggle();

const close = () => {
  open.value = false;
};

watch(open, (value) => {
  emit('openChange', value);
});

const handleViewAll = () => {
  emit('viewAll');
  close();
};

const handleMakeAll = () => {
  emit('makeAll');
};

const handleClear = () => {
  emit('clear');
};

const handleSelect = (item: NotificationItem) => {
  emit('click', item);
  emit('select', item);
  close();
};

const handleRead = (item: NotificationItem) => {
  emit('read', item);
};
</script>
<template>
  <VbenPopover v-model:open="open" content-class="relative right-2 w-90 p-0">
    <template #trigger>
      <div class="mr-2 flex-center h-full" @click.stop="toggle()">
        <VbenIconButton class="bell-button relative text-foreground">
          <span
            v-if="dot"
            class="absolute top-0.5 right-0.5 size-2 rounded-sm bg-primary"
          ></span>
          <Bell class="size-4" />
        </VbenIconButton>
      </div>
    </template>

    <div class="relative">
      <div class="flex items-center justify-between p-4 py-3">
        <div class="text-foreground">{{ $t('ui.widgets.notifications') }}</div>
        <VbenIconButton
          :disabled="notifications.length <= 0"
          :tooltip="$t('ui.widgets.markAllAsRead')"
          @click="handleMakeAll"
        >
          <MailCheck class="size-4" />
        </VbenIconButton>
      </div>
      <VbenScrollbar v-if="notifications.length > 0">
        <ul class="flex! max-h-90 w-full flex-col">
          <template v-for="item in notifications" :key="item.id ?? item.title">
            <li
              class="relative flex w-full cursor-pointer items-center gap-3 border-t border-border p-3 pr-10 hover:bg-accent"
              @click="handleSelect(item)"
            >
              <slot name="content" :item="item">
                <span
                  class="relative flex size-10 shrink-0 overflow-hidden rounded-full"
                >
                  <img
                    :src="item.avatar"
                    class="aspect-square size-full object-cover"
                  />
                </span>
                <div class="flex min-w-0 flex-1 flex-col gap-1">
                  <p class="line-clamp-1 font-semibold leading-5">
                    {{ item.title }}
                  </p>
                  <p class="line-clamp-1 text-xs leading-5 text-muted-foreground">
                    {{ item.message }}
                    <span v-if="item.date"> · {{ item.date }}</span>
                  </p>
                </div>
                <div
                  class="absolute top-1/2 right-3 flex -translate-y-1/2 flex-row gap-1"
                >
                  <slot name="action" :item="item">
                    <slot name="action-prepend" :item="item"></slot>
                    <button
                      v-if="!item.isRead"
                      aria-label="标记已读"
                      class="size-2.5 rounded-full bg-primary transition-transform hover:scale-125"
                      title="标记已读"
                      type="button"
                      @click.stop="handleRead(item)"
                    ></button>
                    <slot name="action-append" :item="item"></slot>
                  </slot>
                </div>
              </slot>
            </li>
          </template>
        </ul>
      </VbenScrollbar>

      <template v-else>
        <div class="flex-center min-h-37.5 w-full text-muted-foreground">
          {{ $t('common.noData') }}
        </div>
      </template>

      <div
        class="flex items-center justify-between border-t border-border px-4 py-3"
      >
        <VbenButton
          :disabled="notifications.length <= 0"
          class="notification-clear-button"
          size="sm"
          variant="ghost"
          @click="handleClear"
        >
          {{ $t('ui.widgets.clearNotifications') }}
        </VbenButton>
        <VbenButton size="sm" @click="handleViewAll">
          {{ $t('ui.widgets.viewAll') }}
        </VbenButton>
      </div>
    </div>
  </VbenPopover>
</template>

<style scoped>
:deep(.bell-button) {
  &:hover {
    svg {
      animation: bell-ring 1s both;
    }
  }
}

:deep(.notification-clear-button) {
  border: 1px solid hsl(var(--border));
}

@keyframes bell-ring {
  0%,
  100% {
    transform-origin: top;
  }

  15% {
    transform: rotateZ(10deg);
  }

  30% {
    transform: rotateZ(-10deg);
  }

  45% {
    transform: rotateZ(5deg);
  }

  60% {
    transform: rotateZ(-5deg);
  }

  75% {
    transform: rotateZ(2deg);
  }
}
</style>
