// TODO: muted automatically, investigate should it be ran for JS or not
// IGNORE_BACKEND: JS

var invokeOrder: String = ""

fun test(x: Double = { invokeOrder += "x"; 1.0 }(), a: String, y: Long = { invokeOrder += "y"; 1 }(), b: String): String {
    return "" + x + a + b + y;
}

fun box(): String {
    val funResult = test(b = { invokeOrder += "K"; "K" }(), a = { invokeOrder += "O"; "O" }())

    if (invokeOrder != "KOxy" || funResult != "1.0OK1") return "fail: $invokeOrder != KOxy or $funResult != 1.0OK1"

    return "OK"
}