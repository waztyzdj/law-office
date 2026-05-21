import type { Recordable, UserInfo } from '@vben/types';

import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { notification } from 'ant-design-vue';
import { defineStore } from 'pinia';

import { getAccessCodesApi, getUserInfoApi, loginApi, logoutApi } from '#/api/system';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param params 登录表单数据
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      const result = await loginApi(params);

      // 如果成功获取到 token
      if (result.token) {
        accessStore.setAccessToken(result.token);

        // 注意：这里不再设置用户信息和权限码
        // 用户信息将在路由守卫中通过 fetchUserInfo() 获取
        // 权限码也将在路由守卫中通过 getAccessCodesApi() 获取

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
        } else {
          // 登录成功后，触发路由守卫，由守卫获取用户信息并生成菜单
          onSuccess
            ? await onSuccess?.()
            : await router.push(preferences.app.defaultHomePath);
        }

        // 暂时不显示成功通知，等获取到用户信息后再显示
        // if (userInfo?.realName) {
        //   notification.success({
        //     description: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
        //     duration: 3,
        //     message: $t('authentication.loginSuccess'),
        //   });
        // }
      }
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  async function logout(redirect: boolean = true) {
    try {
      await logoutApi();
    } catch {
      // 不做任何处理
    }
    resetAllStores();
    accessStore.setLoginExpired(false);

    // 回登录页带上当前路由地址
    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  async function fetchUserInfo() {
    const userInfo = await getUserInfoApi();
    
    // 适配后端返回的数据结构
    const adaptedUserInfo: UserInfo = {
      userId: userInfo.userId || (userInfo as any).userId,
      username: userInfo.username || (userInfo as any).username,
      realName: userInfo.realName || (userInfo as any).realName,
      roles: userInfo.roles || (userInfo as any).roles || [],
      permissions: userInfo.permissions || (userInfo as any).permissions || [],
      homePath: userInfo.homePath || preferences.app.defaultHomePath,
      desc: userInfo.desc || (userInfo as any).desc || '',
      token: accessStore.accessToken || '',
      avatar: userInfo.avatar || (userInfo as any).avatar || '',
    };
    
    userStore.setUserInfo(adaptedUserInfo);
    return adaptedUserInfo;
  }

  function $reset() {
    loginLoading.value = false;
  }

  return {
    $reset,
    authLogin,
    fetchUserInfo,
    loginLoading,
    logout,
  };
});
