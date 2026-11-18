/*
 * Copyright (C) 2025 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0.
 *
 * KuroDeck stand-in: the real helper lives in WindowManager-Shell (SystemUI). A sideloaded
 * launcher cannot create bubbles, so every bubble feature reports disabled.
 */
package com.android.wm.shell.shared.bubbles;

public final class BubbleFlagHelper {
    private BubbleFlagHelper() {}
    public static boolean enableCreateAnyBubble() { return false; }
    public static boolean enableBubbleAnything() { return false; }
    public static boolean enableBubbleToFullscreen() { return false; }
}
