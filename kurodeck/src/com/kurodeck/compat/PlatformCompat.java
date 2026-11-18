/*
 * Copyright (C) 2026 KuroDeck contributors
 * Licensed under the Apache License, Version 2.0.
 */
package com.kurodeck.compat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;

/**
 * Bridges for the platform-only APIs that GrapheneOS Launcher3 calls directly.
 *
 * <p>The stock launcher is a privileged system app compiled against the full framework,
 * including GrapheneOS additions such as {@code GosPackageState}, Storage Scopes and Contact
 * Scopes. KuroDeck is a regular app, so those calls go through reflection. When the framework
 * refuses them (other OS, hidden-API policy, missing permission), each helper returns a
 * neutral value and the dependent feature hides itself.
 */
public final class PlatformCompat {
    private static final String TAG = "KuroDeckCompat";

    private PlatformCompat() {}

    /** GrapheneOS {@code GosPackageStateFlag.STORAGE_SCOPES_ENABLED}. */
    public static final String FLAG_STORAGE_SCOPES = "STORAGE_SCOPES_ENABLED";
    /** GrapheneOS {@code GosPackageStateFlag.CONTACT_SCOPES_ENABLED}. */
    public static final String FLAG_CONTACT_SCOPES = "CONTACT_SCOPES_ENABLED";

    /** Whether GrapheneOS reports {@code flagName} as set for the package. */
    public static boolean hasGosPackageStateFlag(String pkg, UserHandle user, String flagName) {
        try {
            Class<?> flags = Class.forName("android.content.pm.GosPackageStateFlag");
            int flag = flags.getField(flagName).getInt(null);
            Class<?> state = Class.forName("android.content.pm.GosPackageState");
            Method get = state.getMethod("get", String.class, UserHandle.class);
            Object s = get.invoke(null, pkg, user);
            if (s == null) return false;
            Method hasFlag = state.getMethod("hasFlag", int.class);
            return (Boolean) hasFlag.invoke(s, flag);
        } catch (Throwable t) {
            return false;
        }
    }

    /** {@code android.app.StorageScope.createConfigActivityIntent(pkg)} on GrapheneOS. */
    @Nullable
    public static Intent createStorageScopesIntent(String pkg) {
        return invokeIntentFactory("android.app.StorageScope", pkg);
    }

    /** {@code android.ext.cscopes.ContactScopesApi.createConfigActivityIntent(pkg)}. */
    @Nullable
    public static Intent createContactScopesIntent(String pkg) {
        return invokeIntentFactory("android.ext.cscopes.ContactScopesApi", pkg);
    }

    @Nullable
    private static Intent invokeIntentFactory(String className, String pkg) {
        try {
            Method m = Class.forName(className)
                    .getMethod("createConfigActivityIntent", String.class);
            return (Intent) m.invoke(null, pkg);
        } catch (Throwable t) {
            Log.w(TAG, "Unavailable: " + className, t);
            return null;
        }
    }

    /**
     * Starts {@code intent} for {@code user}. Regular apps can only start activities in their
     * own profile, so other profiles fall back to the current one.
     */
    public static void startActivityAsUser(Context context, Intent intent,
            @Nullable Bundle options, UserHandle user) {
        if (!Process.myUserHandle().equals(user)) {
            try {
                Method m = Context.class.getMethod("startActivityAsUser",
                        Intent.class, Bundle.class, UserHandle.class);
                m.invoke(context, intent, options, user);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "startActivityAsUser unavailable, using current user", t);
            }
        }
        context.startActivity(intent, options);
    }
}
