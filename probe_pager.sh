#!/usr/bin/env bash
# ============================================================
# 翻页器按键探测脚本
# 用法：
#   1. 设备打开 USB 调试，连电脑
#   2. bash probe_pager.sh
#   3. 按你的翻页器按键，看屏幕上跳出的按键码
#   4. 把按键码告诉开发者，加入映射表
#
# 依赖：adb（在 PATH 中，或在下方 ADB 变量指定绝对路径）
# ============================================================

ADB="${ADB:-adb}"

echo "============================================"
echo "     翻页器按键探测工具"
echo "============================================"
echo ""

# 1. 检查 adb
if ! command -v "$ADB" >/dev/null 2>&1; then
    echo "[错误] 找不到 adb。"
    echo "       请先安装 platform-tools，或用 ADB=/path/to/adb bash probe_pager.sh"
    exit 1
fi

# 2. 检查设备
echo "[1/3] 检查设备连接..."
DEVICES=$("$ADB" devices | tail -n +2 | grep -w "device" | wc -l)
if [ "$DEVICES" -eq 0 ]; then
    echo "[错误] 没有检测到已授权的设备。"
    echo "       请确认："
    echo "         - 设备已开启『USB调试』"
    echo "         - 数据线已连接"
    echo "         - 设备上已点击『允许USB调试』"
    "$ADB" devices
    exit 1
fi
echo "       检测到 $DEVICES 台设备 ✓"
echo ""

# 3. 设备信息
echo "[2/3] 设备信息："
"$ADB" shell getprop ro.product.model 2>/dev/null | sed 's/^/       型号: /'
"$ADB" shell getprop ro.build.version.release 2>/dev/null | sed 's/^/       Android: /'
"$ADB" shell getprop ro.product.manufacturer 2>/dev/null | sed 's/^/       厂商: /'
echo ""

# 4. 列出输入设备
echo "[3/3] 当前输入设备列表（找你的翻页器）："
"$ADB" shell getevent -pl 2>/dev/null | grep -E "^add device|name:" | sed 's/^/       /' | head -40
echo ""

echo "============================================"
echo "   开始监听按键 —— 现在请按翻页器按键"
echo "   （按 Ctrl+C 停止）"
echo "============================================"
echo ""
echo "常见按键码对照："
echo "  0004 0007 : 方向键 (左/右/上/下)"
echo "  0001 0067 : PageUp"
echo "  0001 0068 : Space"
echo "  0001 0069 : PageDown"
echo "  0001 0072 : 音量+  (KEY_VOLUMEUP)"
echo "  0001 0073 : 音量-  (KEY_VOLUMEDOWN)"
echo "  0001 0163 : 媒体下一曲"
echo "  0001 0165 : 媒体上一曲"
echo ""

"$ADB" shell getevent -lt
