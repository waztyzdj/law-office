import { watch } from 'vue';
import { getPreferences } from '@vben/preferences';

/**
 * 全局字体大小动态调整
 * 
 * 监听偏好设置中的字体大小配置，并应用 0.875 的缩放系数
 * 更新 CSS 变量 --font-size-base 和 --menu-font-size
 */
export function useGlobalFontSize() {
  const preferences = getPreferences();
  
  // 缩放系数：系统字体大小 = 设置的字体大小 × 0.875
  const FONT_SCALE_FACTOR = 0.875;

  // 监听字体大小变化
  watch(
    () => preferences.theme.fontSize,
    (newFontSize) => {
      if (newFontSize) {
        const scaledSize = newFontSize * FONT_SCALE_FACTOR;
        document.documentElement.style.setProperty('--font-size-base', `${scaledSize}px`);
        document.documentElement.style.setProperty('--menu-font-size', `${scaledSize}px`);
      }
    },
    { immediate: true }
  );
}
