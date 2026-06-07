import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:folder-open',
      order: 15,
      title: '文档中心',
    },
    name: 'Document',
    path: '/document',
    redirect: '/document/files',
    children: [
      {
        name: 'DocumentFiles',
        path: '/document/files',
        component: () => import('#/views/document/center/index.vue'),
        meta: {
          icon: 'lucide:folder-open',
          title: '文档中心',
        },
      },
    ],
  },
];

export default routes;
