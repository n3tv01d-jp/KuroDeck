/*
 * Copyright (C) 2025 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 * Copied from frameworks/base/libs/WindowManager/Shell/shared.
 */
package com.android.wm.shell.shared.bubbles.logging

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Entry points for creating Bubbles. */
@Parcelize
enum class EntryPoint : Parcelable {
    TASKBAR_ICON_MENU,
    LAUNCHER_ICON_MENU,
    ALL_APPS_ICON_MENU,
    HOTSEAT_ICON_MENU,
    TASKBAR_ICON_DRAG,
    ALL_APPS_ICON_DRAG,
    NOTIFICATION,
    NOTIFICATION_BUBBLE_BUTTON,
}
