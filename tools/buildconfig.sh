#!/bin/bash
# Copyright (C) 2025 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

show_help() {
  echo "Usage: buildconfig.sh [options]"
  echo "Generates a BuildConfig.java file with launcher build-time flags."
  echo
  echo "Options:"
  echo "  --pkg <package_name>      Set the package name. Defaults to com.android.launcher3."
  echo "  --appId <app_id>          Set the APPLICATION_ID string. Defaults to the package name."
  echo "  -e, --enable <name>       Enable a boolean flag."
  echo "  -d, --disable <name>      Disable a boolean flag."
  echo "  -h, --help                Show this help message."
}


declare -A overrides
overrides[pkg]="com.android.launcher3"


while [[ $# -gt 0 ]]; do
  key="$1"
  case $key in
    -h|--help)
      show_help
      exit 0
      ;;
    --pkg)
      overrides[pkg]="$2"
      shift 2
      ;;
    --appId)
      overrides[appId]="$2"
      shift 2
      ;;
    -e|--enable)
      overrides[$2]="true"
      shift 2
      ;;
    -d|--disable)
      overrides[$2]="false"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

echo "
package ${overrides[pkg]};

public final class BuildConfig {
    public static final String APPLICATION_ID = \"${overrides[appId]:-${overrides[pkg]}}\";

    public static final boolean IS_STUDIO_BUILD = ${overrides[IS_STUDIO_BUILD]:-false};
    public static final boolean QSB_ON_FIRST_SCREEN = ${overrides[QSB_ON_FIRST_SCREEN]:-false};
    public static final boolean IS_DEBUG_DEVICE = ${overrides[IS_DEBUG_DEVICE]:-false};
    public static final boolean WIDGETS_ENABLED = ${overrides[WIDGETS_ENABLED]:-true};
    public static final boolean NOTIFICATION_DOTS_ENABLED = ${overrides[NOTIFICATION_DOTS_ENABLED]:-true};
}
"
