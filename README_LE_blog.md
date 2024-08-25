

# leakcanary：实现fork子进程dump堆快照，避免app冻结

相关项目：  
https://github.com/light-echo-3/KOOM_LE  
https://github.com/light-echo-3/leakcanaryLE  
  
参照koom方案，fork子进程后，在子进程中dump堆快照，避免长时间冻结主进程。

## 引入koom相关模块：
samples/koom-fast-dump  
samples/kwai-android-base  

## 具体实现
- com.example.leakcanary.DebugExampleApplication
```java
class DebugExampleApplication : ExampleApplication() {
  private val TAG = "DebugExampleApplication"

  override fun onCreate() {
    super.onCreate()
    LeakCanary.config = LeakCanary.config.run {
      copy(eventListeners = eventListeners + EventListener {
        if (it is HeapAnalysisDone<*>) {
          LeakUiAppClient(this@DebugExampleApplication).sendHeapAnalysis(it.heapAnalysis)
        }
      },
        heapDumper = HeapDumper {
          if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
          ) {
            // 核心代码就这一行，注意此方法会挂起线程，等待子进程dump完成，不要在UI线程调用！
            ForkJvmHeapDumper.getInstance().dump(it.absolutePath)
          } else {
            Debug.dumpHprofData(it.absolutePath)
          }

        })
    }

  }
}

```


- com.kwai.koom.fastdump.ForkJvmHeapDumper#dump

```java

  @Override
  public synchronized boolean dump(String path) {
    ...
    try {
      //挂起ART虚拟机，并fork一个子进程
      int pid = suspendAndFork();
      if (pid == 0) {//pid=0代表子进程
        // Child process
        Log.i(TAG, "子进程-begin");
        Debug.dumpHprofData(path);//在子进程中dump hropf
        Log.i(TAG, "子进程-end");
        exitProcess();
      } else if (pid > 0) {
        // Parent process
        //恢复虚拟机运行，然后阻塞等待子进程退出
        dumpRes = resumeAndWait(pid);
      }
    } catch (IOException e) {
      Log.e(TAG, "dump failed caused by " + e);
      e.printStackTrace();
    }
    return dumpRes;
  }

```

- HprofDump::SuspendAndFork


```c++

pid_t HprofDump::SuspendAndFork() {
  KCHECKI(init_done_)

  if (android_api_ < __ANDROID_API_R__) {
    suspend_vm_fnc_();
  } else if (android_api_ <= __ANDROID_API_T__) {
    void *self = __get_tls()[TLS_SLOT_ART_THREAD_SELF];
    sgc_constructor_fnc_((void *)sgc_instance_.get(), self, kGcCauseHprof,
                         kCollectorTypeHprof);
    ssa_constructor_fnc_((void *)ssa_instance_.get(), LOG_TAG, true);
    // avoid deadlock with child process
    exclusive_unlock_fnc_(*mutator_lock_ptr_, self);
    sgc_destructor_fnc_((void *)sgc_instance_.get());
  }

  pid_t pid = fork();
  if (pid == 0) {
    // Set timeout for child process
    alarm(60);//这里设置60秒超时，如果60秒内没dump完，进程会退出，导致dump失败（这是debug的时候，经常导致失败的原因）
    prctl(PR_SET_NAME, "forked-dump-process");
  }
  return pid;
}

```

- HprofDump::ResumeAndWait

```c++
bool HprofDump::ResumeAndWait(pid_t pid) {
  KCHECKB(init_done_)

  if (android_api_ < __ANDROID_API_R__) {
    resume_vm_fnc_();
  } else if (android_api_ <= __ANDROID_API_T__) {
    void *self = __get_tls()[TLS_SLOT_ART_THREAD_SELF];
    exclusive_lock_fnc_(*mutator_lock_ptr_, self);
    ssa_destructor_fnc_((void *)ssa_instance_.get());
  }
  int status;
  for (;;) {
      //阻塞等待子进程退出，子进程退出，waitpid会返回子进程pid
    if (waitpid(pid, &status, 0) != -1) {
        //检查是否正常退出
      if (!WIFEXITED(status)) {
        ALOGE("Child process %d exited with status %d, terminated by signal %d",
              pid, WEXITSTATUS(status), WTERMSIG(status));
        return false;//异常退出，返回false
      }
      return true;//正常退出，返回true
    }
    // if waitpid is interrupted by the signal,just call it again
    if (errno == EINTR){
      continue;
    }
    return false;
  }
}
```


----

```java
```
