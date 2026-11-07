<script lang="ts" setup>
import { computed } from 'vue';

import { AuthPageLayout } from '@vben/layouts';
import { preferences } from '@vben/preferences';

const appName = computed(() => import.meta.env.VITE_APP_TITLE);
const appEnName = computed(() => import.meta.env.VITE_APP_EN_TITLE);
const systemName = computed(() => import.meta.env.VITE_APP_SYSTEM_NAME);
const systemDescription = computed(
  () => import.meta.env.VITE_APP_SYSTEM_DESCRIPTION,
);
const logo = computed(() => preferences.logo.source);
const logoDark = computed(() => preferences.logo.sourceDark);
</script>

<template>
  <AuthPageLayout
    :app-name="appName"
    :copyright="false"
    :logo="logo"
    :logo-dark="logoDark"
    :page-description="systemDescription"
    :page-title="systemName"
  >
    <template #logo>
      <div class="absolute top-0 left-0 z-10 flex flex-1">
        <div
          class="mt-4 ml-4 flex flex-1 items-center text-foreground sm:top-6 sm:left-6 lg:text-foreground"
        >
          <img
            v-if="logo"
            :alt="appName"
            :src="logo"
            class="mr-2"
            width="42"
          />
          <div class="m-0 leading-tight">
            <div class="text-xl font-medium">{{ appName }}</div>
            <div class="mt-0.5 text-center text-xs tracking-wide opacity-75">
              {{ appEnName }}
            </div>
          </div>
        </div>
      </div>
    </template>
    <!-- 自定义工具栏 -->
    <!-- <template #toolbar></template> -->
  </AuthPageLayout>
</template>
