package com.ridipager.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager

/**
 * 主界面：
 *  1) 打开本地 TXT 电子书
 *  2) 全屏接管 蓝牙翻页器 / 蓝牙键盘 / 遥控器 的翻页按键
 *
 * 关键点：翻页器配对后，系统会把它当作「蓝牙键盘」，
 * 按下即产生标准 KeyEvent，这里在 dispatchKeyEvent 里统一拦截。
 */
class MainActivity : Activity() {

    private lateinit var reader: ReaderView
    private lateinit var hint: TextView
    private lateinit var toolbar: LinearLayout

    private var lastKeyTime = 0L
    private val keyDebounceMs = 120L   // 防止翻页器长按/连击误触发

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        // 阅读时保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        keepFocusable()
        showBluetoothStatus()
        // APP 启动后自动扫描一次，省得用户手动找书
        autoScanSilently()
    }

    override fun onDestroy() {
        try { reader.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    /** 让本界面必定能收到按键事件（部分定制机默认焦点在别处） */
    private fun keepFocusable() {
        reader.isFocusable = true
        reader.isFocusableInTouchMode = true
        reader.requestFocus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 顶部工具栏
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(0xFFF2F2F2.toInt())
        }
        val btnOpen = Button(this).apply { text = "打开电子书" }
        btnOpen.setOnClickListener { pickFile() }
        val btnPrev = Button(this).apply { text = "◀ 上一页" }
        btnPrev.setOnClickListener { doPrev() }
        val btnNext = Button(this).apply { text = "下一页 ▶" }
        btnNext.setOnClickListener { doNext() }
        toolbar.addView(btnOpen, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f))
        toolbar.addView(btnPrev, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(btnNext, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(toolbar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // 阅读区
        reader = ReaderView(this)
        reader.onPageChanged = { cur, total ->
            runOnUiThread { updateStatus(cur, total) }
        }
        root.addView(reader, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // 底部状态栏
        hint = TextView(this).apply {
            text = "等待翻页器…"
            textSize = 13f
            setPadding(24, 12, 24, 12)
            setBackgroundColor(0xFFF2F2F2.toInt())
        }
        root.addView(hint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        setContentView(root)
    }

    // ==================== 核心：按键拦截 ====================

    /**
     * 在 dispatch 阶段就拦截，优先级高于一切子控件，
     * 保证翻页器按键不会被按钮焦点吞掉。
     *
     * 注意：音量键在部分 ROM 上会先被系统音量条截获，
     * 这里通过重写 onKeyDown + 在 dispatch 双重拦截来兜底。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (KeyMapper.isPagingKey(code)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                if (now - lastKeyTime >= keyDebounceMs) {
                    lastKeyTime = now
                    when (KeyMapper.map(code)) {
                        KeyMapper.ACTION_PREV -> doPrev()
                        KeyMapper.ACTION_NEXT -> doNext()
                    }
                }
            }
            return true   // 消费掉，不再向下传递（同时阻止系统音量条弹出）
        }
        return super.dispatchKeyEvent(event)
    }

    /** 双保险：万一某些 ROM 绕过了 dispatch，这里再拦一次 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (KeyMapper.isPagingKey(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun doNext() {
        if (!reader.hasContent()) { toast("请先打开一本电子书"); return }
        if (!reader.nextPage()) toast("已是最后一页")
    }

    private fun doPrev() {
        if (!reader.hasContent()) { toast("请先打开一本电子书"); return }
        if (!reader.prevPage()) toast("已是第一页")
    }

    /** 刷新底部状态栏：区分 TXT/EPUB/PDF 三种模式 */
    private fun updateStatus(cur: Int = -1, total: Int = -1) {
        if (!reader.hasContent()) {
            hint.text = "等待翻页器…（支持 TXT / EPUB / PDF）"
            return
        }
        val (c, t) = if (cur >= 0 && total >= 0) Pair(cur, total) else reader.pageInfo()
        val modeName = when (reader.currentMode()) {
            ReaderView.Mode.TEXT -> "TXT"
            ReaderView.Mode.EPUB -> "EPUB"
            ReaderView.Mode.PDF -> "PDF"
            else -> ""
        }
        val chap = reader.chapterInfo()?.let { (ci, ct) -> "   ·   第 $ci/$ct 章" } ?: ""
        hint.text = "[$modeName] 第 $c / $t 页$chap   |   翻页器已就绪"
    }

    // ==================== 文件选择 ====================

    private val REQ_PICK = 1001

    /** 支持的格式：TXT / EPUB / PDF */
    private val supportedMimes = arrayOf(
        "text/plain",                       // txt
        "application/epub+zip",             // epub
        "application/pdf"                   // pdf
    )

    private fun pickFile() {
        // 优先用多类型过滤器；部分老 ROM 不识别，则退化为 */*
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, supportedMimes)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(Intent.createChooser(intent, "选择电子书（TXT / EPUB / PDF）"), REQ_PICK)
        } catch (e: Exception) {
            toast("系统无文件选择器，正在扫描存储卡…")
            autoScanSilently()
            if (!reader.hasContent()) toast("未找到电子书，请确认存储卡中有 TXT / EPUB / PDF")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            openUri(uri)
        }
    }

    private fun openUri(uri: android.net.Uri) {
        try {
            val name = queryDisplayName(uri) ?: "book.txt"
            val ext = name.substringAfterLast('.', "").lowercase()
            val tmp = java.io.File(cacheDir, "book_tmp.$ext")
            // 清理旧的临时文件
            cacheDir.listFiles { f -> f.name.startsWith("book_tmp.") }?.forEach { it.delete() }
            contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            }
            hint.text = "正在加载 $name …"
            loadInBackground(tmp.absolutePath, name)
        } catch (e: Exception) {
            toast("打开失败：${e.message}")
        }
    }

    /**
     * 后台解析 → 主线程应用。
     * EPUB 解压 / PDF 打开都可能耗时，必须离开主线程，否则 ANR。
     *
     * 注意：prepareInBackground 的 listener 已在主线程被回调，
     * 所以这里不要再用局部变量跨线程传递结果（会有竞态）。
     */
    private fun loadInBackground(path: String, displayName: String) {
        reader.prepareInBackground(path) { ok, msg ->
            // 此处已在主线程
            toast(msg)
            if (ok) updateStatus() else hint.text = msg
        }
    }

    /** 从 content:// 查询真实文件名（用于判断扩展名） */
    @SuppressLint("Recycle")
    private fun queryDisplayName(uri: android.net.Uri): String? {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment
    }

    /** 启动时静默扫描一次，找到书就自动加载，找不到不打扰用户 */
    private fun autoScanSilently() {
        // 文件扫描本身较轻，但仍放后台避免阻塞首帧
        Thread {
            val dirs = listOf(
                "/sdcard/Books", "/sdcard/books", "/sdcard/Documents",
                "/sdcard/Download", "/sdcard/ridibooks", "/sdcard/ebook",
                "/mnt/sdcard/Books", "/mnt/sdcard/Download"
            )
            val exts = listOf("txt", "epub", "pdf")
            var found: java.io.File? = null
            for (d in dirs) {
                val dir = java.io.File(d)
                if (!dir.isDirectory) continue
                val files = dir.listFiles { f ->
                    f.isFile && exts.any { f.name.lowercase().endsWith(".$it") }
                } ?: continue
                if (files.isNotEmpty()) { found = files[0]; break }
            }
            if (found != null) {
                // prepareInBackground 内部自动异步解析 + 主线程回调
                loadInBackground(found!!.absolutePath, found!!.name)
            }
        }.also { it.isDaemon = true }.start()
    }

    // ==================== 蓝牙状态 ====================

    /** 蓝牙状态只弹一次 Toast，不霸占状态栏（状态栏留给页码） */
    private fun showBluetoothStatus() {
        val adapter = getBtAdapter()
        val name = try { adapter?.name ?: "未知" } catch (_: SecurityException) { "未知" }
        val on = try { adapter?.isEnabled == true } catch (_: SecurityException) { false }
        if (!on) {
            toast("蓝牙未开启，请到系统设置打开蓝牙并配对翻页器")
        } else {
            toast("蓝牙已开启（$name），配对翻页器后按翻页键即可")
        }
        if (!reader.hasContent()) updateStatus()
    }

    private fun getBtAdapter(): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    // ==================== 工具 ====================

    private var toast: Toast? = null
    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            toast?.cancel()
            toast = Toast.makeText(this, msg, Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    override fun onResume() {
        super.onResume()
        keepFocusable()
        showBluetoothStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) keepFocusable()
    }
}
