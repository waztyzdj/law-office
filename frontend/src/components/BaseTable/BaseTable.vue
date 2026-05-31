<script setup lang="ts">
import { computed, ref, useAttrs, useSlots } from 'vue';

import { Button, Card, Space, Table } from 'ant-design-vue';

import type { ButtonProps, TableProps } from 'ant-design-vue';

defineOptions({ name: 'BaseTable', inheritAttrs: false });

export interface BaseTableToolbarButton {
  buttonProps?: ButtonProps & Record<string, any>;
  danger?: boolean;
  disabled?: boolean;
  key: string;
  label: string;
  loading?: boolean;
  onClick?: (
    button: BaseTableToolbarButton,
    event: MouseEvent,
  ) => void | Promise<void>;
  permissionCode?: string;
  type?: ButtonProps['type'];
}

interface Props extends /* @vue-ignore */ TableProps {
  cardClass?: any;
  cardProps?: Record<string, any>;
  showCard?: boolean;
  showToolbar?: boolean;
  toolbarButtons?: BaseTableToolbarButton[];
  toolbarClass?: any;
}

const props = withDefaults(defineProps<Props>(), {
  showCard: true,
  showToolbar: undefined,
  toolbarButtons: () => [],
});

const emit = defineEmits<{
  toolbarButtonClick: [
    button: BaseTableToolbarButton,
    event: MouseEvent,
  ];
}>();

const attrs = useAttrs();
const slots = useSlots();
const tableRef = ref<InstanceType<typeof Table>>();

const tableAttrs = computed(() => {
  const { class: className, style, ...restAttrs } = attrs;

  return restAttrs;
});

const tableProps = computed(() => {
  const {
    cardClass,
    cardProps,
    class: className,
    showCard,
    showToolbar,
    style,
    toolbarButtons,
    toolbarClass,
    ...restProps
  } = props as Props & { class?: any; style?: any };

  return {
    ...restProps,
    pagination: normalizePagination(restProps.pagination),
  };
});

const shouldShowToolbar = computed(
  () =>
    props.showToolbar ??
    Boolean(
      props.toolbarButtons.length > 0 || slots.toolbar || slots.toolbarExtra,
    ),
);

const tableSlotNames = computed(() =>
  Object.keys(slots).filter(
    (name) =>
      !['afterTable', 'beforeTable', 'toolbar', 'toolbarExtra'].includes(name),
  ),
);

function getButtonProps(button: BaseTableToolbarButton) {
  const { buttonProps = {}, danger, disabled, loading, type } = button;

  return {
    danger,
    disabled,
    loading,
    type,
    ...buttonProps,
  };
}

function normalizePagination(pagination: Props['pagination']) {
  if (pagination === false || pagination === undefined) {
    return pagination;
  }

  if (typeof pagination === 'object') {
    const pageConfig = pagination as Record<string, any>;
    const total = Number(pageConfig.total || 0);

    return {
      ...pageConfig,
      current: pageConfig.current ?? pageConfig.pageNum ?? 1,
      hideOnSinglePage: false,
      pageSize: pageConfig.pageSize ?? 10,
      showSizeChanger: pageConfig.showSizeChanger ?? true,
      showTotal: pageConfig.showTotal
        ? () => pageConfig.showTotal(total)
        : () => `共 ${total} 条`,
      total: total > 0 ? total : 1,
    };
  }

  return pagination;
}

async function handleToolbarButtonClick(
  button: BaseTableToolbarButton,
  event: MouseEvent,
) {
  if (button.disabled || button.loading) {
    return;
  }

  await button.onClick?.(button, event);
  emit('toolbarButtonClick', button, event);
}

defineExpose({
  tableRef,
});
</script>

<template>
  <component
    :is="showCard ? Card : 'div'"
    v-bind="showCard ? cardProps : undefined"
    :class="[showCard ? cardClass : undefined, $attrs.class]"
    :style="$attrs.style"
  >
    <div
      v-if="shouldShowToolbar"
      :class="['base-table-toolbar', toolbarClass]"
    >
      <div class="base-table-toolbar-main">
        <Space v-if="toolbarButtons.length > 0">
          <template
            v-for="button in toolbarButtons"
            :key="button.key"
          >
            <Button
              v-if="button.permissionCode"
              v-access:code="button.permissionCode"
              v-bind="getButtonProps(button)"
              @click="(event) => handleToolbarButtonClick(button, event)"
            >
              {{ button.label }}
            </Button>
            <Button
              v-else
              v-bind="getButtonProps(button)"
              @click="(event) => handleToolbarButtonClick(button, event)"
            >
              {{ button.label }}
            </Button>
          </template>
        </Space>
        <slot name="toolbar"></slot>
      </div>
      <div
        v-if="$slots.toolbarExtra"
        class="base-table-toolbar-extra"
      >
        <slot name="toolbarExtra"></slot>
      </div>
    </div>

    <slot name="beforeTable"></slot>

    <Table
      ref="tableRef"
      v-bind="{ bordered: true, rowKey: 'id', ...tableAttrs, ...tableProps }"
    >
      <template
        v-for="name in tableSlotNames"
        :key="name"
        #[name]="slotProps"
      >
        <slot
          :name="name"
          v-bind="slotProps || {}"
        ></slot>
      </template>
    </Table>

    <slot name="afterTable"></slot>
  </component>
</template>

<style scoped>
.base-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.base-table-toolbar-main,
.base-table-toolbar-extra {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
