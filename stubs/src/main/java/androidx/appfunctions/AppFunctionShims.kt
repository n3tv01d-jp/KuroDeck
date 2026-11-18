/*
 * KuroDeck shims for the AppFunctions API surface that Launcher3's (flag-gated, disabled)
 * "workspace functions" module is written against. AOSP builds it with an unreleased
 * androidx.appfunctions snapshot; the published artifacts have a different API, and exposing
 * the workspace to on-device agents is out of scope for KuroDeck. These declarations only let
 * the module compile — nothing registers with the platform AppFunctions service.
 */
package androidx.appfunctions

/** Context handed to an app function call. Never constructed in KuroDeck. */
interface AppFunctionContext

/** Marks a type that can cross the AppFunctions boundary. No-op here. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
annotation class AppFunctionSerializable(val isDescribedByKDoc: Boolean = false)
