package com.yassernull.nullbox.ipc

import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PtyInfo(
    val ptyFd: ParcelFileDescriptor,
    val pid: Int
) : Parcelable
