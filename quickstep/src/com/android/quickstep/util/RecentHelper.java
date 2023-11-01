package com.android.quickstep.util;

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;
import static com.android.quickstep.LauncherLockedStateController.TASK_LOCK_LIST_KEY_WITH_USERID;
import static com.android.quickstep.LauncherLockedStateController.TASK_LOCK_STATE;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.Launcher;
import com.android.quickstep.TaskUtilLockState;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.List;
import java.util.Set;


public class RecentHelper {

    private static RecentHelper sInstance = null;
    private SharedPreferences pref;
    private static final String TAG = RecentHelper.class.getSimpleName();

    public static RecentHelper getInstance() {
        if (sInstance == null) {
            sInstance = new RecentHelper ();
        }
        return sInstance;
    }

    public void clearAllTaskStacks(Context context) {
        try {
            // TODO Add support even launcher is not default
            Launcher launcher = Launcher.getLauncher(context);
            RecentsView recentsView = launcher.getOverviewPanel();
            int taskViewCount = recentsView.getTaskViewCount();
            int currentUserId = Process.myUserHandle().getIdentifier();
            for (int i = 0; i <= taskViewCount; i++) {
                try {
                    List<ActivityManager.RecentTaskInfo> rawTasks = ActivityTaskManager.getInstance()
                            .getRecentTasks(i, RECENT_IGNORE_UNAVAILABLE, currentUserId);
                    for (ActivityManager.RecentTaskInfo recentTaskInfo : rawTasks) {
                        String packageName = recentTaskInfo.baseIntent.getComponent().getPackageName();
                        Task.TaskKey taskKey = new Task.TaskKey(recentTaskInfo);
                        if (taskKey != null) {
                            int taskId = taskKey.id;
                            packageName = packageName.replace("unknown", "");
                            if (!packageName.isEmpty()) {
                                packageName += "#" + UserHandle.getUserId(taskId);
                                boolean taskLockState = TaskUtilLockState.getTaskLockState(context, taskKey.baseIntent.getComponent(), taskKey);
                                if (!isAppLocked(packageName, context) &&
                                        !packageName.contains(BuildConfig.APPLICATION_ID) &&
                                        !taskLockState) {
                                    ActivityManagerWrapper.getInstance().removeTask(taskId);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception exception) {
            Log.e(TAG, "clearAllTaskStacks: ", exception);
            ActivityManagerWrapper.getInstance().removeAllRecentTasks();
        }
    }


    public boolean isAppLocked(String packageName, Context context) {
        final String LOG_TAG = "Process";
        final String COMMON_END = ", skip...";
        if (pref == null) {
            pref = context.getSharedPreferences(TASK_LOCK_STATE, Context.MODE_PRIVATE);
        }
        Set<String> lockedApps = pref.getStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, null);
        if (lockedApps != null && !lockedApps.isEmpty()) {
            for (String lockedPackage : lockedApps) {
                if (lockedPackage.contains(packageName)) {
                    Log.i(LOG_TAG, "Found locked package: " + packageName + COMMON_END);
                    return true;
                }
            }
        } else if (lockedApps != null) {
            Log.w(LOG_TAG, "Locked apps list is empty.");
        } else {
            Log.w(LOG_TAG, "Locked apps list is null.");
        }
        return false;
    }

}
