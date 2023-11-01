package com.android.quickstep;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.android.systemui.shared.recents.model.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskUtilLockState {

    private static final String SEPARATOR = "#";
    private static final List<String> mLockedApps = new ArrayList<> ();
    private static final String RECENT_LOCK_LIST = "recent_lock_list";
    private static final ExecutorService mIoExecutor = Executors.newSingleThreadExecutor();
    private static final String TAG = "TaskUtilLockState";


    public static boolean getTaskLockState(Context context, ComponentName componentName, Task.TaskKey taskKey) {
        return updateSpecifiedTaskLockState(context, componentName, taskKey);
    }
    private static boolean updateSpecifiedTaskLockState(Context context, ComponentName componentName, Task.TaskKey taskKey) {
        boolean taskLockState = LauncherLockedStateController.getInstance(context)
                .getTaskLockState(componentName.toShortString(), taskKey.userId);
        Log.d(TAG, "updateSpecifiedTaskLockState: Checking if the task is locked: " + taskLockState);
        if (taskLockState) {
            setTaskLockState(context, taskKey.baseIntent.getComponent(), taskLockState, taskKey);
            Log.i(TAG, "updateSpecifiedTaskLockState: Task is locked, clearing the lock state.");
        }
        return taskLockState;
    }
    public static void setTaskLockState(Context context, ComponentName componentName, boolean isState, Task.TaskKey taskKey) {
        LauncherLockedStateController.getInstance(context).setTaskLockState(componentName.toShortString(), isState, taskKey.userId);
        String formatLockedAppStr = toFormatLockedAppStr(componentName.getPackageName(), taskKey.userId);
        if (isState) {
            addLockedApp(formatLockedAppStr);
        } else {
            removeLockedApp(formatLockedAppStr);
        }
    }
    public static String toFormatLockedAppStr(String packageName, int userId) {
        return packageName + SEPARATOR + userId;
    }
    private static void saveLockedApps(List<String> lockedApps) {
        if (lockedApps != null) {
            mIoExecutor.execute(() -> saveListToFileSync(lockedApps));
        }
    }
    public static void saveListToFileSync(List<String> lockedApps) {
        Bundle bundle = new Bundle();
        bundle.putStringArrayList(RECENT_LOCK_LIST, new ArrayList<>(lockedApps));
    }
    public static void addLockedApp(String appStr) {
        if (!mLockedApps.contains(appStr)) {
            Log.d(TAG, "addLockedApp: " + appStr);
            mLockedApps.add(appStr);
            saveLockedApps(mLockedApps);
        }
    }
    public static void removeLockedApp(String appStr) {
        if (mLockedApps.contains(appStr)) {
            Log.d(TAG, "removeLockedApp: " + appStr);
            mLockedApps.remove(appStr);
            saveLockedApps(mLockedApps);
        }
    }
}
