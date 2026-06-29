import type { Ref } from 'vue';

import type {
  InstanceDetailInfo,
  OperationRecordInfo,
  RuntimeTaskInfo,
} from '#/api/workflow';
import type { ProcessProgressNode } from '../runtimeTypes';

import { computed } from 'vue';

import {
  formatApprovalProgress,
  getStatusMeta,
} from '../../../components/status';

const hiddenApprovalRecordActions = new Set(['branch_match']);

export function useRuntimeProgressNodes(
  detail: Ref<InstanceDetailInfo | undefined>,
) {
  const processInstance = computed(() => detail.value?.processInstance);
  const chronologicalRecords = computed(() =>
    [...(detail.value?.records ?? [])]
      .filter((record) => !hiddenApprovalRecordActions.has(record.action ?? ''))
      .sort((a, b) =>
        String(a.operateTime ?? '').localeCompare(String(b.operateTime ?? '')),
      ),
  );
  const currentTasks = computed(() => detail.value?.currentTasks ?? []);

  const processProgressNodes = computed<ProcessProgressNode[]>(() => {
    const nodes: ProcessProgressNode[] = chronologicalRecords.value.map(
      (record, index) => ({
        action: record.action,
        actor: formatActor(record),
        comment: formatRecordComment(record),
        id: record.id ?? `record-${index}`,
        name: formatRecordNode(record),
        status: 'done',
        time: record.operateTime,
      }),
    );

    currentTasks.value.forEach((task, index) => {
      nodes.push({
        approvalMode: task.approvalMode,
        actor: formatTaskActor(task),
        groupProgress: formatApprovalProgress(task),
        id: task.id ?? `current-${index}`,
        name: task.taskName ?? task.nodeId ?? '当前任务',
        status: 'current',
        taskId: task.id,
        time: task.startTime,
      });
    });

    if (isProcessFinished(processInstance.value?.status)) {
      const lastRecord = chronologicalRecords.value.at(-1);
      nodes.push({
        actor: '系统',
        comment: getProcessEndComment(processInstance.value?.status),
        id: `${processInstance.value?.id ?? 'process'}-end`,
        name: '流程结束',
        resultStatus: processInstance.value?.status,
        status: 'end',
        time: processInstance.value?.endTime || lastRecord?.operateTime,
      });
    }

    return nodes;
  });

  return {
    processProgressNodes,
  };
}

function formatActor(record: OperationRecordInfo) {
  return record.operatorRealname ?? record.operatorUsername ?? '-';
}

function formatTaskActor(task: RuntimeTaskInfo) {
  return (
    task.assigneeRealname ||
    task.assigneeUsername ||
    task.candidateAssigneeNames ||
    '-'
  );
}

function formatRecordNode(record: OperationRecordInfo) {
  return record.nodeName ?? record.nodeId ?? '流程记录';
}

function formatRecordComment(record: OperationRecordInfo) {
  return record.comment?.trim() || '无';
}

function isProcessFinished(status?: string) {
  return ['approved', 'rejected', 'terminated', 'withdrawn'].includes(status || '');
}

function getProcessEndComment(status?: string) {
  const statusLabel = getStatusMeta(status).label;
  return statusLabel === '-' ? '流程已结束' : `流程${statusLabel}`;
}
