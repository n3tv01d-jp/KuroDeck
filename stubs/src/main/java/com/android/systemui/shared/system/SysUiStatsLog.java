/*
 * KuroDeck stand-in for the statsd-generated SysUiStatsLog. Only the enum constants that
 * Launcher3 reads are provided; a non-system app cannot write statsd atoms.
 */
package com.android.systemui.shared.system;

public final class SysUiStatsLog {
    private SysUiStatsLog() {}
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_UNKNOWN = 0;
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_MAIN = 1;
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_WORK = 2;
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_PRIVATE = 3;
    public static final int LAUNCHER_UICHANGED__USER_TYPE__TYPE_CLONED = 4;
}
