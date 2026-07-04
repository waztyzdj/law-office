import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:home',
      order: -2,
      title: '首页',
    },
    name: 'Home',
    path: '/home',
    redirect: '/home/workbench',
    children: [
      {
        component: () => import('#/views/home/workbench/index.vue'),
        meta: {
          icon: 'carbon:workspace',
          title: '工作台',
        },
        name: 'HomeWorkbench',
        path: '/home/workbench',
      },
      {
        component: () => import('#/views/home/admin/card/index.vue'),
        meta: {
          icon: 'lucide:layout-grid',
          title: '卡片管理',
        },
        name: 'HomeWorkbenchCardManage',
        path: '/home/card',
      },
      {
        component: () => import('#/views/home/admin/quick-entry/index.vue'),
        meta: {
          icon: 'lucide:route',
          title: '快捷菜单管理',
        },
        name: 'HomeWorkbenchQuickEntryManage',
        path: '/home/quick-entry',
      },
    ],
  },
];

export default routes;
