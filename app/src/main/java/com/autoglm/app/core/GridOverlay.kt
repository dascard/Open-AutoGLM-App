package com.autoglm.app.core

import android.graphics.*

/** 视觉策略枚举 */
enum class VisualStrategy {
    AUTO, // 自动选择
    SOM, // Set-of-Marks 标记模式
    GRID, // 网格模式
    NONE // 纯视觉模式（无标记）
}

/** 网格叠加工具类 在截图上绘制网格，用于 AI 视觉定位 */
object GridOverlay {

    private const val DEFAULT_GRID_SIZE = 10 // 10x10 网格

    private val linePaint =
            Paint().apply {
                color = Color.argb(120, 255, 255, 255) // 半透明白色
                strokeWidth = 2f
                isAntiAlias = true
            }

    private val labelPaint =
            Paint().apply {
                color = Color.WHITE
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

    private val labelBgPaint =
            Paint().apply {
                color = Color.argb(180, 0, 0, 0) // 半透明黑色背景
                isAntiAlias = true
            }

    // 列标签 A-J
    private val columnLabels = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J")

    /**
     * 在 Bitmap 上绘制网格
     * @param bitmap 原始截图
     * @param gridSize 网格尺寸 (默认 10x10)
     * @return 标注后的 Bitmap
     */
    fun drawGrid(bitmap: Bitmap, gridSize: Int = DEFAULT_GRID_SIZE): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val cellWidth = bitmap.width.toFloat() / gridSize
        val cellHeight = bitmap.height.toFloat() / gridSize

        // 绘制垂直线
        for (i in 1 until gridSize) {
            val x = i * cellWidth
            canvas.drawLine(x, 0f, x, bitmap.height.toFloat(), linePaint)
        }

        // 绘制水平线
        for (i in 1 until gridSize) {
            val y = i * cellHeight
            canvas.drawLine(0f, y, bitmap.width.toFloat(), y, linePaint)
        }

        // 在每个格子中心绘制标签 (只绘制部分关键位置，避免太乱)
        // 绘制角落和中心的标签
        val keyPositions =
                listOf(
                        0 to 0,
                        0 to 4,
                        0 to 9, // 第一行
                        4 to 0,
                        4 to 4,
                        4 to 9, // 中间行
                        9 to 0,
                        9 to 4,
                        9 to 9 // 最后一行
                )

        for ((row, col) in keyPositions) {
            if (col < columnLabels.size && row < gridSize) {
                val label = "${columnLabels[col]}${row + 1}"
                val centerX = col * cellWidth + cellWidth / 2
                val centerY = row * cellHeight + cellHeight / 2

                // 绘制标签背景
                val bgRadius = 18f
                canvas.drawCircle(centerX, centerY, bgRadius, labelBgPaint)

                // 绘制标签文字
                val textY = centerY + (labelPaint.textSize / 3)
                canvas.drawText(label, centerX, textY, labelPaint)
            }
        }

        return result
    }

    /**
     * 将网格引用转换为屏幕坐标
     * @param gridRef 网格引用，如 "E5", "A1", "J10"
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param gridSize 网格尺寸
     * @return Pair<x, y> 屏幕坐标，如果解析失败返回 null
     */
    fun gridToCoordinates(
            gridRef: String,
            screenWidth: Int,
            screenHeight: Int,
            gridSize: Int = DEFAULT_GRID_SIZE
    ): Pair<Int, Int>? {
        if (gridRef.length < 2) return null

        // 解析列 (A-J)
        val colChar = gridRef[0].uppercaseChar()
        val colIndex = colChar - 'A'
        if (colIndex < 0 || colIndex >= gridSize) return null

        // 解析行 (1-10)
        val rowStr = gridRef.substring(1)
        val rowIndex = rowStr.toIntOrNull()?.minus(1) ?: return null
        if (rowIndex < 0 || rowIndex >= gridSize) return null

        // 计算格子中心坐标
        val cellWidth = screenWidth.toFloat() / gridSize
        val cellHeight = screenHeight.toFloat() / gridSize

        val x = (colIndex * cellWidth + cellWidth / 2).toInt()
        val y = (rowIndex * cellHeight + cellHeight / 2).toInt()

        return Pair(x, y)
    }

    /**
     * 根据 UI 元素数量选择最佳策略
     * @param uiElementCount UI 元素数量
     * @param manualStrategy 手动指定的策略 (如果是 AUTO 则自动选择)
     * @return 实际使用的策略
     */
    fun selectStrategy(uiElementCount: Int, manualStrategy: VisualStrategy): VisualStrategy {
        if (manualStrategy != VisualStrategy.AUTO) {
            return manualStrategy
        }

        // 自动选择逻辑
        return when {
            uiElementCount in 5..20 -> VisualStrategy.SOM // 元素数量适中，使用标记
            uiElementCount > 0 -> VisualStrategy.GRID // 元素太多或太少，使用网格
            else -> VisualStrategy.GRID // 无法解析，使用网格
        }
    }
}
