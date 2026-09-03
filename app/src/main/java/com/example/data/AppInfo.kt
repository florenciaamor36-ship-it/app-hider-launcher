package com.example.data

import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val className: String,
    val iconBitmap: ImageBitmap? = null
)
