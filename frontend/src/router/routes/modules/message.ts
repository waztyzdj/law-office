import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:message-square',
      order: 20,
      title: '站内消息',
    },
    name: 'Message',
    path: '/message',
    redirect: '/message/inbox',
    children: [
      {
        component: () => import('#/views/message/inbox/index.vue'),
        meta: {
          icon: 'lucide:inbox',
          title: '收件箱',
        },
        name: 'MessageInbox',
        path: '/message/inbox',
      },
      {
        component: () => import('#/views/message/sent/index.vue'),
        meta: {
          icon: 'lucide:send',
          title: '发件箱',
        },
        name: 'MessageSent',
        path: '/message/sent',
      },
    ],
  },
];

export default routes;
