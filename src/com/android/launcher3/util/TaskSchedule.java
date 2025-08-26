/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.util;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.Executor;

public class TaskSchedule {

    static final String TAG = "TaskSchedule";
    public static void runAllTasksInExecutor(ArrayList<SafelyRunnable> runnableList, Executor executor, int parallelTasks) {
        int taskSize = runnableList.size();
        parallelTasks = Math.min(taskSize, parallelTasks);
        Object LOCK = new Object();
        for (int i = 0; i < parallelTasks; ++i) {
            executor.execute(() -> {
                while (true) {
                    SafelyRunnable runnable = getTask(runnableList, LOCK);
                    if (runnable == null) {
                        break;
                    }
                    runnable.run();
                }
            });
        }
    }

    public static void runTasks(ArrayList<SafelyRunnable> runnableList, Executor executor, int parallelTasks, long timeOut) {
        int taskSize = runnableList.size();
        parallelTasks = Math.min(taskSize - 1, parallelTasks);
        Object LOCK = new Object();
        final IntCounter intCounter = new IntCounter();
        for (int i = 0; i < parallelTasks; ++i) {
            executor.execute(() -> {
                while (true) {
                    SafelyRunnable runnable = getTask(runnableList, LOCK);
                    if (runnable == null) {
                        break;
                    }
                    runnable.run();
                    synchronized (LOCK) {
                        intCounter.add();
                    }
                }
            });
        }
        long taskDoneTime = -1;
        while (true) {
            SafelyRunnable runnable = getTask(runnableList, LOCK);
            if (runnable == null) {
                if (intCounter.isEqual(taskSize)) {
                    break;
                }
                if (taskDoneTime == -1) {
                    taskDoneTime = SystemClock.uptimeMillis();
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Log.e(TAG, "InterruptedException-e:" + e.getMessage());
                }
                long now = SystemClock.uptimeMillis();
                if (now - taskDoneTime > timeOut) {
                    Log.e(TAG, "runTasks>>>>>>>>>>>>>>>>>>>>>>>time out");
                    break;
                }
                continue;
            }
            runnable.run();
            synchronized (LOCK) {
                intCounter.add();
            }
        }
    }

    static SafelyRunnable getTask(ArrayList<SafelyRunnable> runnableList, final Object LOCK) {
        synchronized (LOCK) {
            if (runnableList.isEmpty()) {
                return null;
            }
            int size = runnableList.size();
            return runnableList.remove(size - 1);
        }
    }

    public static abstract class SafelyRunnable implements Runnable {

        @Override
        public final void run() {
            try {
                onTaskRun();
            } catch (Throwable e) {
                Log.e(TAG,"SafelyRunnable-Throwable:" + e.getMessage());
            }
        }

        public abstract void onTaskRun();
    }

    public static class IntCounter {
        public int mCounter = 0;

        public void add() {
            this.mCounter += 1;
        }

        public boolean isEqual(int value) {
            return this.mCounter == value;
        }
    }
}