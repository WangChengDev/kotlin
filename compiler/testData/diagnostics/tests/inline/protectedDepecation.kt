// !DIAGNOSTICS: -EXPOSED_PARAMETER_TYPE -NOTHING_TO_INLINE

open class A {
    protected fun test() {

    }

    inline fun call() {
        <!PROTECTED_CALL_FROM_PUBLIC_INLINE!>test<!>()
    }

    @PublishedApi
    internal inline fun call2() {
        test()
    }
}

class B : A() {
    inline fun testB() {
        <!PROTECTED_CALL_FROM_PUBLIC_INLINE!>test<!>()
    }
}


internal class AInternal {
    protected fun test() {

    }

    inline fun call() {
        test()
    }

    @PublishedApi
    internal inline fun call2() {
        test()
    }
}