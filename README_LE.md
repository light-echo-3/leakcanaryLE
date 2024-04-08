
牛X实现
@see leakcanary/internal/Objects.kt
```kotlin

internal inline fun <reified T : Any> noOpDelegate(): T {
  val javaClass = T::class.java
  return Proxy.newProxyInstance(
    javaClass.classLoader, arrayOf(javaClass), NO_OP_HANDLER
  ) as T
}

private val NO_OP_HANDLER = InvocationHandler { _, _, _ ->
  // no op
}

```
