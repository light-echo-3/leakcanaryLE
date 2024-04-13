

## demo已实现fork子进程dump堆内存，避免了app冻结

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

## 源码分析
[LeakCanary源码分析（2.10最新版）](https://juejin.cn/post/7179146545613242429#heading-17)

## leakCanary  shark源码解析
https://blog.csdn.net/stone_cold_cool/article/details/120645631
https://juejin.cn/post/7043755844718034958
