package com.autoglm.app.core

import android.graphics.*

/** UI 元素数据类 */
data class UIElement(
        val id: Int, // 标记编号
        val bounds: Rect, // 边界矩形
        val text: String?, // 文本内容
        val description: String?, // 内容描述
        val className: String? // 类名
) {
    /** 获取中心点 */
    val centerX: Int
        get() = bounds.centerX()
    val centerY: Int
        get() = bounds.centerY()
}

/** Set-of-Marks 标记工具类 在截图上绘制数字标记，用于精准点击 */
object SetOfMarks {

    private val markPaint =
            Paint().apply {
                color = Color.WHITE
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

    private val bgPaint =
            Paint().apply {
                color = Color.parseColor("#E91E63") // 粉红色背景
                isAntiAlias = true
            }

    private val borderPaint =
            Paint().apply {
                color = Color.parseColor("#E91E63")
                style = Paint.Style.STROKE
                strokeWidth = 3f
                isAntiAlias = true
            }

    /**
     * 在 Bitmap 上绘制标记
     * @param bitmap 原始截图
     * @param elements 可点击元素列表
     * @return 标注后的 Bitmap
     */
    fun drawMarks(bitmap: Bitmap, elements: List<UIElement>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        elements.forEach { element ->
            val bounds = element.bounds

            // 绘制元素边框
            canvas.drawRect(bounds, borderPaint)

            // 计算标记位置（左上角）
            val markRadius = 18f
            val markX = bounds.left + markRadius
            val markY = bounds.top + markRadius

            // 绘制圆形背景
            canvas.drawCircle(markX, markY, markRadius, bgPaint)

            // 绘制数字
            val textY = markY + (markPaint.textSize / 3)
            canvas.drawText(element.id.toString(), markX, textY, markPaint)
        }

        return result
    }

    /** 根据标记 ID 查找元素 */
    fun findElementById(elements: List<UIElement>, markId: Int): UIElement? {
        return elements.find { it.id == markId }
    }
}
