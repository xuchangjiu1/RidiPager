package com.ridipager.app

import android.view.KeyEvent

/**
 * 按键归一化：把各种翻页器 / 蓝牙键盘 / 遥控器发出的按键，
 * 统一翻译成「上一页 / 下一页」两种意图。
 *
 * 覆盖市面常见翻页器（含淘宝 HBTR002 这类 HID 键盘模拟设备）的按键码：
 *  - PageUp / PageDown        标准翻页键
 *  - 方向键 左/右/上/下        部分翻页器映射为方向键
 *  - 音量键 上/下              很多拍照/翻页器默认发音量键
 *  - 媒体键 上一曲/下一曲       部分设备走媒体控制
 *  - 回车 / 空格               单击型翻页器
 *  - 数字键 1/2 等             少数自定义固件
 */
object KeyMapper {

    const val ACTION_NONE = 0
    const val ACTION_PREV = 1
    const val ACTION_NEXT = 2

    fun map(keyCode: Int): Int = when (keyCode) {
        // ---- 标准翻页键 ----
        KeyEvent.KEYCODE_PAGE_UP -> ACTION_PREV
        KeyEvent.KEYCODE_PAGE_DOWN -> ACTION_NEXT

        // ---- 方向键 ----
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP -> ACTION_PREV
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN -> ACTION_NEXT

        // ---- 音量键（最常见） ----
        KeyEvent.KEYCODE_VOLUME_UP -> ACTION_PREV
        KeyEvent.KEYCODE_VOLUME_DOWN -> ACTION_NEXT

        // ---- 媒体控制键（部分翻页器 / 蓝牙自拍杆固件） ----
        KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> ACTION_PREV
        KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> ACTION_NEXT

        // ---- 单击型 / 确认型 ----
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER -> ACTION_NEXT

        // ---- 少数自定义固件的数字键 ----
        KeyEvent.KEYCODE_1 -> ACTION_PREV
        KeyEvent.KEYCODE_2 -> ACTION_NEXT

        else -> ACTION_NONE
    }

    /** 该按键是否属于"翻页类"，属于的话主界面会拦截、不再向下传递 */
    fun isPagingKey(keyCode: Int): Boolean = map(keyCode) != ACTION_NONE
}
