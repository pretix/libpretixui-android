To avoid everyone working on pretix apps having to compile native code themselves, this folder
contains binary libraries.

The binaries in this folder have been compiled by Raphael Michel in January 2021 using this
source tree:
https://github.com/raphaelm/UVCCamera/tree/raphaelm-fixes

The build environment consisted of plain Android SDK and NDK installations, provided by the
docker image available on docker hub as bitriseio/android-ndk:v2021_01_09-08_39-b2261

The usbCameraCommon.aar binary contains the following components:

UVCCamera
=========
Copyright (c) 2014-2017 saki t_saki@serenegiant.com
Apache License 2.0
