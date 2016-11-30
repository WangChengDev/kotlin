@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package cases.inline

@kotlin.PublishedApi
internal fun exposedForInline() {}

@kotlin.PublishedApi
internal class InternalClassExposed
    @kotlin.PublishedApi
    internal constructor() {

    @kotlin.PublishedApi
    internal fun funExposed() {}

    // TODO: Cover unsupported cases: requires correctly reflecting annotations from properties
    /*
    @kotlin.PublishedApi
    internal var propertyExposed: String? = null

    @JvmField
    @kotlin.PublishedApi
    internal var fieldExposed: String? = null
    */

}
