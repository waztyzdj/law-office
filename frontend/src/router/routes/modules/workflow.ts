import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:workflow',
      order: 20,
      title: '审批中心',
    },
    name: 'Workflow',
    path: '/workflow',
    redirect: '/workflow/todo',
    children: [
      {
        name: 'WorkflowTodo',
        path: '/workflow/todo',
        component: () => import('#/views/workflow/runtime/todo/index.vue'),
        meta: {
          icon: 'lucide:list-todo',
          title: '我的待办',
        },
      },
      {
        name: 'WorkflowDone',
        path: '/workflow/done',
        component: () => import('#/views/workflow/runtime/done/index.vue'),
        meta: {
          icon: 'lucide:check-check',
          title: '我的已办',
        },
      },
      {
        name: 'WorkflowStarted',
        path: '/workflow/started',
        component: () => import('#/views/workflow/runtime/started/index.vue'),
        meta: {
          icon: 'lucide:file-clock',
          title: '我发起的',
        },
      },
      {
        name: 'WorkflowStart',
        path: '/workflow/start',
        component: () => import('#/views/workflow/runtime/start/index.vue'),
        meta: {
          icon: 'lucide:clipboard-pen',
          title: '发起申请',
        },
      },
      {
        name: 'WorkflowCategory',
        path: '/workflow/category',
        component: () => import('#/views/workflow/admin/category/index.vue'),
        meta: {
          icon: 'lucide:tags',
          title: '流程分类',
        },
      },
      {
        name: 'WorkflowForm',
        path: '/workflow/form',
        component: () => import('#/views/workflow/admin/form/index.vue'),
        meta: {
          icon: 'lucide:file-text',
          title: '表单设计',
        },
      },
      {
        name: 'WorkflowProcess',
        path: '/workflow/process',
        component: () => import('#/views/workflow/admin/process/index.vue'),
        meta: {
          icon: 'lucide:route',
          title: '流程设计',
        },
      },
      {
        name: 'WorkflowInstanceDetail',
        path: '/workflow/detail',
        component: () => import('#/views/workflow/runtime/detail/index.vue'),
        meta: {
          hideInMenu: true,
          title: '审批详情',
        },
      },
      {
        name: 'WorkflowTaskHandle',
        path: '/workflow/task',
        component: () => import('#/views/workflow/runtime/task/index.vue'),
        meta: {
          hideInMenu: true,
          title: '任务办理',
        },
      },
    ],
  },
];

export default routes;
