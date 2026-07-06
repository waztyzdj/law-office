import type { Router } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';
import { startProgress, stopProgress } from '@vben/utils';

import { notification } from 'ant-design-vue';

import {
  FALLBACK_HOME_PATH,
  isLegacyTemplateHomePath,
} from '#/constants/routes';
import { $t } from '#/locales';
import { accessRoutes, coreRouteNames } from '#/router/routes';
import { useAuthStore } from '#/store';

import { generateAccess } from './access';

interface AccessibleMenuLike {
  children?: AccessibleMenuLike[];
  meta?: {
    hideInMenu?: boolean;
  };
  path?: string;
  redirect?: string;
  show?: boolean;
}

function decodeRedirectPath(path?: null | string) {
  if (!path) {
    return '';
  }
  try {
    return decodeURIComponent(path);
  } catch {
    return '';
  }
}

function isSafeHomePath(path?: string) {
  return Boolean(path && !isLegacyTemplateHomePath(path));
}

function resolveFirstAccessibleMenuPath(menus: AccessibleMenuLike[]): string {
  for (const menu of menus) {
    if (menu.show === false || menu.meta?.hideInMenu) {
      continue;
    }

    if (menu.children?.length) {
      const childPath = resolveFirstAccessibleMenuPath(menu.children);
      if (childPath) {
        return childPath;
      }
    }

    const path = menu.redirect || menu.path;
    if (path && isSafeHomePath(path)) {
      return path;
    }
  }
  return '';
}

function resolveSafeHomePath(
  homePath?: string,
  accessibleMenus: AccessibleMenuLike[] = [],
): string {
  if (homePath && isSafeHomePath(homePath)) {
    return homePath;
  }

  const firstMenuPath = resolveFirstAccessibleMenuPath(accessibleMenus);
  if (firstMenuPath) {
    return firstMenuPath;
  }

  if (isSafeHomePath(preferences.app.defaultHomePath)) {
    return preferences.app.defaultHomePath;
  }
  return FALLBACK_HOME_PATH;
}

function resolveSafeRedirectPath(
  router: Router,
  redirectPath: string,
  fallbackPath: string,
) {
  if (!redirectPath || isLegacyTemplateHomePath(redirectPath)) {
    return fallbackPath;
  }
  const resolved = router.resolve(redirectPath);
  if (resolved.name === 'FallbackNotFound' || resolved.matched.length === 0) {
    return fallbackPath;
  }
  return redirectPath;
}

async function ensureAccessReady(router: Router) {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const authStore = useAuthStore();

  if (accessStore.isAccessChecked) {
    return userStore.userInfo || (await authStore.fetchUserInfo());
  }

  const userInfo = userStore.userInfo || (await authStore.fetchUserInfo());
  const userRoles = userInfo.roles ?? [];
  const accessCodes = (userInfo as any).permissions || [];
  accessStore.setAccessCodes(accessCodes);

  const { accessibleMenus, accessibleRoutes } = await generateAccess({
    roles: userRoles,
    router,
    routes: accessRoutes,
  });

  accessStore.setAccessMenus(accessibleMenus);
  accessStore.setAccessRoutes(accessibleRoutes);
  accessStore.setIsAccessChecked(true);

  const safeHomePath = resolveSafeHomePath(
    userInfo.homePath,
    accessibleMenus as AccessibleMenuLike[],
  );
  if (userInfo.homePath !== safeHomePath) {
    const normalizedUserInfo = {
      ...userInfo,
      homePath: safeHomePath,
    };
    userStore.setUserInfo(normalizedUserInfo);
    return normalizedUserInfo;
  }

  return userInfo;
}

/**
 * 通用守卫配置
 * @param router
 */
function setupCommonGuard(router: Router) {
  // 记录已经加载的页面
  const loadedPaths = new Set<string>();

  router.beforeEach((to) => {
    to.meta.loaded = loadedPaths.has(to.path);

    // 页面加载进度条
    if (!to.meta.loaded && preferences.transition.progress) {
      startProgress();
    }
    return true;
  });

  router.afterEach((to) => {
    // 记录页面是否加载,如果已经加载，后续的页面切换动画等效果不在重复执行

    loadedPaths.add(to.path);

    // 关闭页面加载进度条
    if (preferences.transition.progress) {
      stopProgress();
    }
  });
}

/**
 * 权限访问守卫配置
 * @param router
 */
function setupAccessGuard(router: Router) {
  router.beforeEach(async (to, from) => {
    const accessStore = useAccessStore();

    // 基本路由，这些路由不需要进入权限拦截
    if (coreRouteNames.includes(to.name as string)) {
      if (to.path === LOGIN_PATH && accessStore.accessToken) {
        const userInfo = await ensureAccessReady(router);
        return resolveSafeRedirectPath(
          router,
          decodeRedirectPath(to.query?.redirect as string),
          resolveSafeHomePath(
            userInfo.homePath,
            accessStore.accessMenus as AccessibleMenuLike[],
          ),
        );
      }
      if (accessStore.accessToken) {
        await ensureAccessReady(router);
      }
      return true;
    }

    // accessToken 检查
    if (!accessStore.accessToken) {
      // 明确声明忽略权限访问权限，则可以访问
      if (to.meta.ignoreAccess) {
        return true;
      }

      // 没有访问权限，跳转登录页面
      if (to.fullPath !== LOGIN_PATH) {
        return {
          path: LOGIN_PATH,
          // 如不需要，直接删除 query
          query:
            to.fullPath === preferences.app.defaultHomePath
              ? {}
              : { redirect: encodeURIComponent(to.fullPath) },
          // 携带当前跳转的页面，登录后重新跳转该页面
          replace: true,
        };
      }
      return to;
    }

    // 是否已经生成过动态路由
    if (accessStore.isAccessChecked) {
      return true;
    }

    const userInfo = await ensureAccessReady(router);
    
    // 显示登录成功通知
    if (userInfo?.realName) {
      notification.success({
        description: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
        duration: 3,
        message: $t('authentication.loginSuccess'),
      });
    }
    
    const redirectPath = (from.query.redirect ??
      (to.path === preferences.app.defaultHomePath
        ? userInfo.homePath || preferences.app.defaultHomePath
        : to.fullPath)) as string;
    const safeRedirectPath = resolveSafeRedirectPath(
      router,
      decodeRedirectPath(redirectPath),
      resolveSafeHomePath(
        userInfo.homePath,
        accessStore.accessMenus as AccessibleMenuLike[],
      ),
    );

    return {
      ...router.resolve(safeRedirectPath),
      replace: true,
    };
  });
}

/**
 * 项目守卫配置
 * @param router
 */
function createRouterGuard(router: Router) {
  /** 通用 */
  setupCommonGuard(router);
  /** 权限访问 */
  setupAccessGuard(router);
}

export { createRouterGuard };
