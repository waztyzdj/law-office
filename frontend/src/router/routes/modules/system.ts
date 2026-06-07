import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:settings',
      order: 10,
      title: '系统管理',
    },
    name: 'System',
    path: '/system',
    children: [
      {
        name: 'SystemUser',
        path: '/system/user',
        component: () => import('#/views/system/user/index.vue'),
        meta: {
          icon: 'lucide:users',
          title: '用户管理',
        },
      },
      {
        name: 'SystemRole',
        path: '/system/role',
        component: () => import('#/views/system/role/index.vue'),
        meta: {
          icon: 'lucide:shield-check',
          title: '角色管理',
        },
      },
      {
        name: 'SystemMenu',
        path: '/system/menu',
        component: () => import('#/views/system/menu/index.vue'),
        meta: {
          icon: 'lucide:menu',
          title: '菜单管理',
        },
      },
      {
        name: 'SystemDepart',
        path: '/system/depart',
        component: () => import('#/views/system/depart/index.vue'),
        meta: {
          icon: 'lucide:building-2',
          title: '部门管理',
        },
      },
      {
        name: 'SystemDict',
        path: '/system/dict',
        component: () => import('#/views/system/dict/index.vue'),
        meta: {
          icon: 'lucide:book-open-text',
          title: '字典管理',
        },
      },
      {
        name: 'SystemFiles',
        path: '/system/files',
        component: () => import('#/views/document/center/index.vue'),
        meta: {
          icon: 'lucide:folder-open',
          title: '文件中心',
        },
      },
      {
        name: 'SystemCategory',
        path: '/system/category',
        component: () => import('#/views/system/category/index.vue'),
        meta: {
          icon: 'lucide:tags',
          title: '通用类型管理',
        },
      },
      {
        name: 'SystemTenant',
        path: '/system/tenant',
        component: () => import('#/views/system/tenant/index.vue'),
        meta: {
          icon: 'lucide:landmark',
          title: '租户管理',
        },
      },
      {
        name: 'SystemLog',
        path: '/system/log',
        component: () => import('#/views/system/log/index.vue'),
        meta: {
          icon: 'lucide:scroll-text',
          title: '日志管理',
        },
      },
    ],
  },
];

export default routes;
