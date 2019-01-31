/* com/google/android/libraries/launcherclient/ILauncherOverlay.aidl
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package com.google.android.libraries.launcherclient;

import android.view.WindowManager/*.LayoutParams*/;
import android.os.Bundle;
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback;

interface ILauncherOverlay {
    oneway void startScroll();

    oneway void onScroll(in float progress);

    oneway void endScroll();

    oneway void windowAttached(in WindowManager.LayoutParams lp, in ILauncherOverlayCallback cb, in int flags);

    oneway void windowDetached(in boolean isChangingConfigurations);

    oneway void closeOverlay(in int flags);

    oneway void onPause();

    oneway void onResume();

    oneway void openOverlay(in int flags);

    oneway void requestVoiceDetection(in boolean start);

    String getVoiceSearchLanguage();

    boolean isVoiceDetectionRunning();

    boolean hasOverlayContent();

    oneway void windowAttached2(in Bundle bundle, in ILauncherOverlayCallback cb);

    oneway void unusedMethod();

    oneway void setActivityState(in int flags);

    boolean startSearch(in byte[] data, in Bundle bundle);
}
