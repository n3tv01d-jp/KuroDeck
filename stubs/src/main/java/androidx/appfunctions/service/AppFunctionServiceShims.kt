/* See androidx/appfunctions/AppFunctionShims.kt. */
package androidx.appfunctions.service

/** Marks a function exposed through AppFunctions. No-op here. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class AppFunction(val isEnabled: Boolean = true, val isDescribedByKDoc: Boolean = false)

/** Configuration object the Application hands to the AppFunctions runtime. Inert here. */
class AppFunctionConfiguration private constructor() {
    interface Provider {
        val appFunctionConfiguration: AppFunctionConfiguration
    }

    class Builder {
        fun <T : Any> addEnclosingClassFactory(
            enclosingClass: Class<T>,
            factory: () -> T,
        ): Builder = this

        fun build(): AppFunctionConfiguration = AppFunctionConfiguration()
    }
}
