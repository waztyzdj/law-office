import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';

import type { InstanceDetailInfo } from '#/api/workflow';

import { getWorkflowInstanceDetail } from '#/api/workflow';

export function useWorkflowDetailPage() {
  const route = useRoute();
  const loading = ref(false);
  const detail = ref<InstanceDetailInfo>();

  const instanceId = computed(() => String(route.query.id ?? ''));
  const formInstance = computed(() => detail.value?.formInstance);
  const formSnapshotSchemaJson = computed(
    () => formInstance.value?.formSchemaSnapshotJson ?? '[]',
  );
  const formSnapshotOptionJson = computed(
    () => formInstance.value?.formOptionSnapshotJson ?? '{}',
  );
  const formSnapshotDataJson = computed(
    () => formInstance.value?.formDataJson ?? '{}',
  );

  async function loadData() {
    if (!instanceId.value) {
      return;
    }

    loading.value = true;
    try {
      detail.value = await getWorkflowInstanceDetail(instanceId.value);
    } finally {
      loading.value = false;
    }
  }

  return {
    detail,
    formSnapshotDataJson,
    formSnapshotOptionJson,
    formSnapshotSchemaJson,
    instanceId,
    loadData,
    loading,
  };
}
