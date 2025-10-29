package com.troubashare.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Represents the effective display area for content that uses ContentScale.Fit.
 * This accounts for aspect ratio letterboxing.
 */
data class EffectiveDisplayArea(
    val width: Float,
    val height: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Calculate the effective display area for content rendered with ContentScale.Fit.
 *
 * ContentScale.Fit maintains aspect ratio while fitting within the canvas bounds,
 * which may result in letterboxing (black bars) on either top/bottom or left/right.
 *
 * This function calculates:
 * - The actual area where content is displayed
 * - The offset from canvas origin to content origin
 *
 * @param bitmap The content bitmap (can be null for fallback to full canvas)
 * @param canvasSize The size of the canvas where content is displayed
 * @return EffectiveDisplayArea containing dimensions and offsets
 */
fun calculateEffectiveDisplayArea(
    bitmap: Bitmap?,
    canvasSize: Size
): EffectiveDisplayArea {
    if (bitmap == null) {
        // Fallback to full canvas if no bitmap available
        return EffectiveDisplayArea(
            width = canvasSize.width,
            height = canvasSize.height,
            offsetX = 0f,
            offsetY = 0f
        )
    }

    val bitmapAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val canvasAspectRatio = canvasSize.width / canvasSize.height

    return if (bitmapAspectRatio > canvasAspectRatio) {
        // Content is wider - fit to width, letterbox top/bottom
        val effectiveHeight = canvasSize.width / bitmapAspectRatio
        EffectiveDisplayArea(
            width = canvasSize.width,
            height = effectiveHeight,
            offsetX = 0f,
            offsetY = (canvasSize.height - effectiveHeight) / 2f
        )
    } else {
        // Content is taller - fit to height, letterbox left/right
        val effectiveWidth = canvasSize.height * bitmapAspectRatio
        EffectiveDisplayArea(
            width = effectiveWidth,
            height = canvasSize.height,
            offsetX = (canvasSize.width - effectiveWidth) / 2f,
            offsetY = 0f
        )
    }
}

/**
 * Overload for ImageBitmap (Compose type).
 */
fun calculateEffectiveDisplayArea(
    bitmap: ImageBitmap?,
    canvasSize: Size
): EffectiveDisplayArea {
    if (bitmap == null) {
        return EffectiveDisplayArea(
            width = canvasSize.width,
            height = canvasSize.height,
            offsetX = 0f,
            offsetY = 0f
        )
    }

    val bitmapAspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
    val canvasAspectRatio = canvasSize.width / canvasSize.height

    return if (bitmapAspectRatio > canvasAspectRatio) {
        // Content is wider - fit to width, letterbox top/bottom
        val effectiveHeight = canvasSize.width / bitmapAspectRatio
        EffectiveDisplayArea(
            width = canvasSize.width,
            height = effectiveHeight,
            offsetX = 0f,
            offsetY = (canvasSize.height - effectiveHeight) / 2f
        )
    } else {
        // Content is taller - fit to height, letterbox left/right
        val effectiveWidth = canvasSize.height * bitmapAspectRatio
        EffectiveDisplayArea(
            width = effectiveWidth,
            height = canvasSize.height,
            offsetX = (canvasSize.width - effectiveWidth) / 2f,
            offsetY = 0f
        )
    }
}
