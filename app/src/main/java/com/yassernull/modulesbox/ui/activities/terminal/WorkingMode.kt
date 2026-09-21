package com.yassernull.modulesbox.ui.activities.terminal

/**
 * Terminal session working modes. Chroot is intentionally not ported — Alpine Linux
 * is proot only, matching the requested scope.
 */
object WorkingMode {
    const val DISTRIBUTION = 0
    const val ANDROID = 1
    const val SHIZUKU = 2
    const val ROOT = 3
    const val DISTRIBUTION_ROOT = 4
    const val DISTRIBUTION_SHIZUKU = 5
}

/**
 * Distribution permission chosen during first-launch setup. Determines where the
 * Alpine distribution lives:
 *
 *  - [DEFAULT_ROOT]: app-private storage (`<dataDir>/local`); sessions run as the app
 *    user (proot) or root (su).
 *  - [SHIZUKU_ROOT]: `/data/local/tmp/modules-box`; sessions run via Shizuku (proot) or
 *    root (su).
 */
object DistroPermission {
    const val DEFAULT_ROOT = 0
    const val SHIZUKU_ROOT = 1
}
