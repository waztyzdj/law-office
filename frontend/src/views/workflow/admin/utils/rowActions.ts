import type { VNodeChild } from 'vue';

import { h } from 'vue';

interface VersionActionRecord {
  status?: string;
}

interface VersionActionEmits<T extends VersionActionRecord> {
  copyAsDraft: (record: T) => void;
  copyTemplate?: (record: T) => void;
  delete: (record: T) => void;
  design: (record: T) => void;
  edit: (record: T) => void;
  history: (record: T) => void;
  publish: (record: T) => void;
  viewDesign: (record: T) => void;
}

export const workflowDangerActionStyle = { color: '#ff4d4f' };

export function buildVersionActionLinks<T extends VersionActionRecord>(
  record: T,
  emits: VersionActionEmits<T>,
  extraActions: VNodeChild[] = [],
) {
  const actions: VNodeChild[] = [];

  if (record.status === 'draft') {
    actions.push(
      h('a', { onClick: () => emits.design(record) }, '设计'),
      h('a', { onClick: () => emits.edit(record) }, '编辑'),
      h('a', { onClick: () => emits.publish(record) }, '发布'),
      h(
        'a',
        {
          onClick: () => emits.delete(record),
          style: workflowDangerActionStyle,
        },
        '删除',
      ),
    );
  } else {
    actions.push(
      h('a', { onClick: () => emits.viewDesign(record) }, '查看设计'),
    );
  }

  actions.push(...extraActions);
  if (emits.copyTemplate) {
    actions.push(
      h('a', { onClick: () => emits.copyTemplate?.(record) }, '复制模板'),
    );
  }
  actions.push(h('a', { onClick: () => emits.history(record) }, '历史版本'));
  if (record.status !== 'draft') {
    actions.push(
      h('a', { onClick: () => emits.copyAsDraft(record) }, '新建版本'),
    );
  }

  return actions;
}
