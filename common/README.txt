To avoid everyone working on pretix apps having to compile native code themselves, this folder
contains binary libraries.

This contains the transitive dependency "libcommon" by saki4510t since it isn't available
on a reliable maven archive and we don't want to trust the GitHub repository to stay around
forever. The binary included is taken from here:
https://github.com/saki4510t/libcommon/blob/master/repository/com/serenegiant/common/2.12.4/common-2.12.4.aar

The common.aar binary contains the following components:

common
======
Copyright (c) 2014-2017 saki t_saki@serenegiant.com
Apache License 2.0
