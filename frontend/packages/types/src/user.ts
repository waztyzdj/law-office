import type { BasicUserInfo } from '@vben-core/typings';

/** 用户信息 */
interface UserInfo extends BasicUserInfo {
  /**
   * 用户描述
   */
  desc: string;
  /**
   * 首页地址
   */
  homePath: string;

  /**
   * accessToken
   */
  token: string;

  /**
   * 权限码
   */
  permissions?: string[];

  /**
   * 当前租户ID
   */
  tenantId?: string;

  /**
   * 当前租户名称
   */
  tenantName?: string;
}

export type { UserInfo };
