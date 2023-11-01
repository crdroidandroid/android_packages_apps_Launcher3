
package com.android.quickstep;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class manages the locked state of recent apps in the launcher.
 */
public class LauncherLockedStateController {
    // Constants for preference keys
    public static final String RECENT_TASK_LOCKED_LIST = "com_android_systemui_recent_task_lockd_list";
    private static final String RECENT_TASK_LOCKED_LIST_BK = "com_android_systemui_recent_task_locked_bk";

    private static boolean mReloaded;
    private static LauncherLockedStateController sInstance;
    private static int time;

    private final String TAG = "LauncherLockedStateController";
    public static final String TASK_LOCK_LIST_KEY = "task_lock_list";
    public static final String TASK_LOCK_LIST_KEY_WITH_USERID = "task_lock_list_with_userid";
    public static final String TASK_LOCK_STATE = "tasklockstate";

    private final HandlerThread mBGThread;
    private final Handler mBGThreadHandler;
    private final Context mContext;

    private Set<String> mLockedListWithUserId;
    private List<String> mLockedPackageNameListWithUserId;
    private SharedPreferences mSp;

    private LauncherLockedStateController(Context context) {
        this.mContext = context;
        HandlerThread handlerThread = new HandlerThread("Recents-LauncherLockedStateController", 10);
        this.mBGThread = handlerThread;
        handlerThread.start();
        this.mBGThreadHandler = new Handler(this.mBGThread.getLooper());
        initPackageNameList(false);
    }

    /**
     * Initializes the list of locked package names.
     *
     * @param z A boolean flag indicating whether to force initialization.
     */
    public void initPackageNameList(boolean z) {
        if (!mReloaded || !z) {
            String packageName = this.mContext.getPackageName();
            if (!TextUtils.isEmpty(packageName) && (TextUtils.isEmpty(packageName) || packageName.contains("launcher"))) {
                SharedPreferences sharedPreferences = this.mContext.getSharedPreferences("tasklockstate", Context.MODE_PRIVATE);
                this.mSp = sharedPreferences;
                this.mLockedListWithUserId = sharedPreferences.getStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, new HashSet<>());
                this.mLockedPackageNameListWithUserId = new ArrayList<>();
                int currentUserId = ActivityManagerWrapper.getInstance().getCurrentUserId();
                Log.d(TAG, "init userId tasklock list: " +
                        this.mLockedListWithUserId.size() + ", id:" + currentUserId + ", " + mReloaded + ", " + z + ", pkgName:" + packageName);
                if (this.mLockedListWithUserId.isEmpty()) {
                    boolean lockedListFromProvider = getLockedListFromProvider(currentUserId);
                    Log.d(TAG, "hasbackup = " + lockedListFromProvider);
                    if (lockedListFromProvider) {
                        buildPkgNameList();
                    }
                } else {
                    buildPkgNameList();
                    mReloaded = true;
                }
                writeToProvider();
                int i = time + 1;
                time = i;
                if (i > 5) {
                    mReloaded = true;
                }
            }
        }
    }

    private void buildPkgNameList() {
        for (String str : this.mLockedListWithUserId) {
            String[] split = str.split("/");
            String substring = str.substring(str.lastIndexOf("#") + 1);
            this.mLockedPackageNameListWithUserId.add(appendUserWithoutBrace(split[0], substring.substring(0, substring.length() - 1)));
        }
    }

    /**
     * Sets the lock state of a task.
     *
     * @param str The task identifier string.
     * @param z   A boolean flag indicating the lock state.
     * @param i   The user ID.
     */
    public void setTaskLockState(String str, boolean z, int i) {
        String[] split = str.split("/");
        String valueOf = String.valueOf(i);
        String appendUserWithBrace = appendUserWithBrace(str, valueOf);
        if (split[0] != null) {
            if (z) {
                this.mLockedListWithUserId.add(appendUserWithBrace);
                this.mLockedPackageNameListWithUserId.add(appendUserWithoutBrace(split[0], valueOf));
            } else {
                this.mLockedListWithUserId.remove(appendUserWithBrace);
                this.mLockedPackageNameListWithUserId.remove(appendUserWithoutBrace(split[0], valueOf));
            }
            SharedPreferences.Editor edit = this.mSp.edit();
            edit.clear();
            edit.putStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, this.mLockedListWithUserId);
            edit.apply();
            writeToProvider();
            if (!mReloaded) {
                try {
                    if (Settings.System.getStringForUser(this.mContext.getContentResolver(), RECENT_TASK_LOCKED_LIST_BK, ActivityManagerWrapper.getInstance().getCurrentUserId()) == null) {
                        Settings.System.putStringForUser(this.mContext.getContentResolver(), RECENT_TASK_LOCKED_LIST_BK, "done", ActivityManagerWrapper.getInstance().getCurrentUserId());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "setTaskLockState error : ", e);
                }
            }
        }
    }

    /**
     * Gets the lock state of a task.
     *
     * @param str The task identifier string.
     * @param i   The user ID.
     * @return A boolean indicating the lock state of the task.
     */
    public boolean getTaskLockState(String str, int i) {
        String valueOf = String.valueOf(i);
        Set<String> set = this.mLockedListWithUserId;
        if (set != null) {
            return set.contains(appendUserWithBrace(str, valueOf));
        }
        return false;
    }

    /**
     * Retrieves the singleton instance of LauncherLockedStateController.
     *
     * @param context The application context.
     * @return The instance of LauncherLockedStateController.
     */
    public static LauncherLockedStateController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LauncherLockedStateController(context);
        }
        return sInstance;
    }

    /**
     * Removes the lock state of a task for a specific user.
     *
     * @param str The task identifier string.
     * @param i   The user ID.
     * TODO Remove task of the uninstalled apps
     */
    public void removeTaskLockState(final String str, final int i) {
        if (i != -1) {
            this.mBGThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    int userId = UserHandle.getUserId(i);
                    String str2 = "{" + str + "/";
                    Log.d(TAG, "uninstall Lock task , " + str + ", " + userId);
                    ArrayList<String> arrayList = new ArrayList<>(LauncherLockedStateController.this.mLockedListWithUserId);
                    int size = arrayList.size();
                    for (int i2 = 0; i2 < size; i2++) {
                        String str3 = arrayList.get(i2);
                        if (str3.startsWith(str2)) {
                            if (str3.endsWith(userId + "}")) {
                                LauncherLockedStateController launcherLockedStateController = LauncherLockedStateController.this;
                                launcherLockedStateController.setTaskLockState(launcherLockedStateController.removeUserWithBrace(str3), false, userId);
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * Checks if a task is locked.
     *
     * @param str The task identifier string.
     * @return A boolean indicating if the task is locked.
     */
    public boolean isTaskLocked(String str) {
        return this.mLockedPackageNameListWithUserId.contains(str);
    }

    /**
     * Appends user identifier without braces to a string.
     *
     * @param str  The input string.
     * @param str2 The user identifier.
     * @return The modified string.
     */
    private String appendUserWithoutBrace(String str, String str2) {
        if (str == null) {
            return null;
        }
        return str.replace("{", "") + "#" + str2;
    }

    /**
     * Appends user identifier with braces to a string.
     *
     * @param str  The input string.
     * @param str2 The user identifier.
     * @return The modified string.
     */
    private String appendUserWithBrace(String str, String str2) {
        if (str == null) {
            return null;
        }
        return str.replace("}", "") + "#" + str2 + "}";
    }

    /**
     * Removes user identifier with braces from a string.
     *
     * @param str The input string.
     * @return The modified string.
     */
    private String removeUserWithBrace(String str) {
        if (str == null) {
            return null;
        }
        return str.substring(0, str.lastIndexOf("#")) + "}";
    }

    private void writeToProvider() {
        StringBuilder sb = new StringBuilder();
        for (String str : this.mLockedListWithUserId) {
            sb.append(str);
        }
        writeLockedListToProvider(sb.toString(), ActivityManagerWrapper.getInstance().getCurrentUserId());
    }

    /**
     * Writes the locked task list to the provider.
     *
     * @param str The locked task list string.
     * @param i   The user ID.
     */
    public void writeLockedListToProvider(final String str, final int i) {
        this.mBGThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Settings.System.putStringForUser(
                        LauncherLockedStateController.this.mContext.getContentResolver(),
                        LauncherLockedStateController.RECENT_TASK_LOCKED_LIST, str, i);
                } catch (Exception e) {
                    Log.e(TAG, "writeLockedListToProvider error: ", e);
                }
            }
        });
    }

    /**
     * Dumps the locked recent app list for debugging purposes.
     *
     * @param printWriter The PrintWriter for output.
     */
    public void dump(PrintWriter printWriter) {
        printWriter.println("LOCKED RECENT APP list: ");
        Set<String> set = this.mLockedListWithUserId;
        String[] strArr = set.toArray(new String[set.size()]);
        printWriter.println();
        printWriter.println("with userId: " + strArr.length);
        for (int i = 0; i < strArr.length; i++) {
            printWriter.print("  ");
            printWriter.println(strArr[i]);
        }
        printWriter.println();
        printWriter.println("with userId: " + this.mLockedPackageNameListWithUserId.size());
        for (int i2 = 0; i2 < this.mLockedPackageNameListWithUserId.size(); i2++) {
            printWriter.print("  ");
            printWriter.println(this.mLockedPackageNameListWithUserId.get(i2));
        }
        printWriter.println();
        int currentUserId = ActivityManagerWrapper.getInstance().getCurrentUserId();
        try {
            printWriter.println("RECENT_TASK_LOCKED_LIST: " + Settings.System.getStringForUser(this.mContext.getContentResolver(), RECENT_TASK_LOCKED_LIST, currentUserId));
        } catch (Exception e) {
            Log.e(TAG, "dump error: ", e);
        }
        printWriter.println();
    }

    private boolean getLockedListFromProvider(int i) {
        String str;
        try {
            str = Settings.System.getStringForUser(this.mContext.getContentResolver(), RECENT_TASK_LOCKED_LIST_BK, i);
        } catch (Exception e) {
            Log.e(TAG, "writeLockedListToProvider error: ", e);
            str = null;
        }
        if (str == null) {
            return false;
        }
        if ("done".equals(str)) {
            mReloaded = true;
            return false;
        }
        String[] split = str.split("\\}");
        if (split != null && split.length > 0) {
            for (int i2 = 0; i2 < split.length; i2++) {
                split[i2] = split[i2] + "}";
                this.mLockedListWithUserId.add(split[i2]);
            }
        }
        SharedPreferences.Editor edit = this.mSp.edit();
        edit.clear();
        edit.putStringSet(TASK_LOCK_LIST_KEY_WITH_USERID, this.mLockedListWithUserId);
        edit.apply();
        try {
            Settings.System.putStringForUser(this.mContext.getContentResolver(), RECENT_TASK_LOCKED_LIST_BK, "done", i);
        } catch (Exception e2) {
            Log.e(TAG, "writeLockedListToProvider error: ", e2);
        }
        return true;
    }
}
