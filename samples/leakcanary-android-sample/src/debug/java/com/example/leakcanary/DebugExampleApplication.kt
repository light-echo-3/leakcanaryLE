package com.example.leakcanary

import android.os.Build
import android.os.Debug
import android.util.Log
import com.kwai.koom.fastdump.ForkJvmHeapDumper
import leakcanary.EventListener
import leakcanary.EventListener.Event.HeapAnalysisDone
import leakcanary.HeapDumper
import leakcanary.LeakCanary
import org.leakcanary.internal.LeakUiAppClient

class DebugExampleApplication : ExampleApplication() {
  private val TAG = "DebugExampleApplication"

  override fun onCreate() {
    super.onCreate()

    // TODO We need to decide whether to show the activity icon based on whether
    //  the app library is here (?). Though ideally the embedded activity is also a separate
    //  optional module.
    LeakCanary.config = LeakCanary.config.run {
      copy(eventListeners = eventListeners + EventListener {
        // TODO Move this into an EventListener class, maybe the standard one
        //  TODO Detect if app installed or not and delegate to std leakcanary if not.
        if (it is HeapAnalysisDone<*>) {
          LeakUiAppClient(this@DebugExampleApplication).sendHeapAnalysis(it.heapAnalysis)
        }
      },
        heapDumper = HeapDumper {
          if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
          ) {
            Log.d(TAG, "------heapDumper: thread=${Thread.currentThread()}")
            // 核心代码就这一行，注意此方法会等待子进程返回采集结果，不要在UI线程调用！
            ForkJvmHeapDumper.getInstance().dump(it.absolutePath)
//            Debug.dumpHprofData(it.absolutePath)
          } else {
            Debug.dumpHprofData(it.absolutePath)
          }

        })
    }


  }
}
