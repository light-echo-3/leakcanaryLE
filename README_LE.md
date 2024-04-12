
## 牛X实现
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


## debug
debug开关，官方默认debug模式下，不触发leak检测
@see leakcanary.internal.DebuggerControl.isDebuggerAttached
