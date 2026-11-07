package com.sentinel.quantum.security

import android.graphics.Bitmap

internal sealed interface MmsThumbnailState {
    data object Loading : MmsThumbnailState
    data class Ready(val bitmap: Bitmap) : MmsThumbnailState
    data object Unavailable : MmsThumbnailState
}
