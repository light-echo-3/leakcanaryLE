/*
 * Copyright 2020 Kwai, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * A jvm hprof dumper which use fork and don't block main process.
 *
 * @author Rui Li <lirui05@kuaishou.com>
 */

package com.kwai.koom.fastdump;

import java.io.IOException;

import android.os.Build;
import android.os.Debug;
import android.util.Log;

public class ForkJvmHeapDumper implements HeapDumper {
  private static final String TAG = "ForkJvmHeapDumper";
  private boolean mLoadSuccess;

  private static class Holder {
    private static final ForkJvmHeapDumper INSTANCE = new ForkJvmHeapDumper();
  }

  public static ForkJvmHeapDumper getInstance() {
    return ForkJvmHeapDumper.Holder.INSTANCE;
  }

  private ForkJvmHeapDumper() {}

  private void init () {
    if (mLoadSuccess) {
      return;
    }
    if (Monitor_SoKt.loadSoQuietly("koom-fast-dump")) {
      mLoadSuccess = true;
      nativeInit();
    }
  }

  @Override
  public synchronized boolean dump(String path) {
    Log.i(TAG, "dump=" + path + ",thread=" + Thread.currentThread());
    if (!sdkVersionMatch()) {
      throw new UnsupportedOperationException("dump failed caused by sdk version not supported!");
    }
    init();
    if (!mLoadSuccess) {
      Log.e(TAG, "dump failed caused by so not loaded!");
      return false;
    }

    boolean dumpRes = false;
    try {
      Log.i(TAG, "before suspend and fork.");
      //挂起ART虚拟机，并fork一个子进程
      int pid = suspendAndFork();
      if (pid == 0) {//pid=0代表子进程
        // Child process
        Log.i(TAG, "子进程-begin");
        Debug.dumpHprofData(path);//在子进程中dump hropf
        Log.i(TAG, "子进程-end");
        exitProcess();
      } else if (pid > 0) {
        Log.i(TAG, "主进程-continue-begin");
        // Parent process
        //恢复虚拟机运行，然后阻塞等待子进程退出
        dumpRes = resumeAndWait(pid);
        Log.i(TAG, "dump " + dumpRes + ", notify from pid " + pid);
        Log.i(TAG, "主进程-continue-end");
      }
    } catch (IOException e) {
      Log.e(TAG, "dump failed caused by " + e);
      e.printStackTrace();
    }
    return dumpRes;
  }

  private boolean sdkVersionMatch() {
    return Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
  }

  /**
   * Init before do dump.
   */
  private native void nativeInit();

  /**
   * Suspend the whole ART, and then fork a process for dumping hprof.
   *
   * @return return value of fork
   */
  private native int suspendAndFork();

  /**
   * Resume the whole ART, and then wait child process to notify.
   *
   * @param pid pid of child process.
   */
  private native boolean resumeAndWait(int pid);

  /**
   * Exit current process.
   */
  private native void exitProcess();
}
