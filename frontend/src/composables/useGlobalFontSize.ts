import { nextTick, watch } from 'vue';
import { getPreferences } from '@vben/preferences';

const DEFAULT_FONT_SIZE = 14;

/**
 * 全局字体大小动态调整
 *
 * 监听偏好设置中的主题配置，并同步更新字体 CSS 变量。
 * 系统基础字体与菜单字体保持同一字号，避免主题切换时出现界面尺寸跳变。
 */
export function useGlobalFontSize() {
  const preferences = getPreferences();

  const applyFontSize = () => {
    const fontSize = preferences.theme.fontSize || DEFAULT_FONT_SIZE;

    document.documentElement.style.setProperty(
      '--font-size-base',
      `${fontSize}px`,
    );
    document.documentElement.style.setProperty(
      '--menu-font-size',
      `${fontSize}px`,
    );
  };

  watch(
    () => ({ ...preferences.theme }),
    () => {
      applyFontSize();
      void nextTick(applyFontSize);
    },
    { immediate: true },
  );
}
