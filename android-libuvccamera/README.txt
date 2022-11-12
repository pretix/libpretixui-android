To avoid everyone working on pretix apps having to compile native code themselves, this folder
contains binary libraries.

The binaries in this folder have been compiled by Raphael Michel in January 2021 using this
source tree:
https://github.com/raphaelm/UVCCamera/tree/raphaelm-fixes

The build environment consisted of plain Android SDK and NDK installations, provided by the
docker image available on docker hub as bitriseio/android-ndk:v2021_01_09-08_39-b2261

The libuvccamera.aar binary contains the following components:

UVCCamera
=========
Copyright (c) 2014-2017 saki t_saki@serenegiant.com
Apache License 2.0

libusb
======
Copyright © 2001 Johannes Erdfelt <johannes@erdfelt.com>
Copyright © 2007-2009 Daniel Drake <dsd@gentoo.org>
Copyright © 2010-2012 Peter Stuge <peter@stuge.se>
Copyright © 2008-2013 Nathan Hjelm <hjelmn@users.sourceforge.net>
Copyright © 2009-2013 Pete Batard <pete@akeo.ie>
Copyright © 2009-2013 Ludovic Rousseau <ludovic.rousseau@gmail.com>
Copyright © 2010-2012 Michael Plante <michael.plante@gmail.com>
Copyright © 2011-2013 Hans de Goede <hdegoede@redhat.com>
Copyright © 2012-2013 Martin Pieuchot <mpi@openbsd.org>
Copyright © 2012-2013 Toby Gray <toby.gray@realvnc.com>

LGPBL License

libuvc
======
Copyright (c) Ken Tossell
BSD 3-clause license

rapidjson
=========
Copyright (c) Milo Yip
MIT license

libjpeg-turbo
=============

Copyright 2009 Pierre Ossman <ossman@cendio.se> for Cendio AB
Copyright (C) 1999-2006, MIYASAKA Masaru.
Copyright (C)2009-2016 D. R. Commander.  All Rights Reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

- Redistributions of source code must retain the above copyright notice,
  this list of conditions and the following disclaimer.
- Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
- Neither the name of the libjpeg-turbo Project nor the names of its
  contributors may be used to endorse or promote products derived from this
  software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS",
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

as well as

This software is copyright (C) 1991-2016, Thomas G. Lane, Guido Vollbeding.
All Rights Reserved except as specified below.

Permission is hereby granted to use, copy, modify, and distribute this
software (or portions thereof) for any purpose, without fee, subject to these
conditions:
(1) If any part of the source code for this software is distributed, then this
README file must be included, with this copyright and no-warranty notice
unaltered; and any additions, deletions, or changes to the original files
must be clearly indicated in accompanying documentation.
(2) If only executable code is distributed, then the accompanying
documentation must state that "this software is based in part on the work of
the Independent JPEG Group".
(3) Permission for use of this software is granted only if the user accepts
full responsibility for any undesirable consequences; the authors accept
NO LIABILITY for damages of any kind.

These conditions apply to any software derived from or based on the IJG code,
not just to the unmodified library.  If you use our work, you ought to
acknowledge us.

Permission is NOT granted for the use of any IJG author's name or company name
in advertising or publicity relating to this software or products derived from
it.  This software may be referred to only as "the Independent JPEG Group's
software".

We specifically permit and encourage the use of this software as the basis of
commercial products, provided that all warranty or liability claims are
assumed by the product vendor.


The Unix configuration script "configure" was produced with GNU Autoconf.
It is copyright by the Free Software Foundation but is freely distributable.
The same holds for its supporting scripts (config.guess, config.sub,
ltmain.sh).  Another support script, install-sh, is copyright by X Consortium
but is also freely distributable.

The IJG distribution formerly included code to read and write GIF files.
To avoid entanglement with the Unisys LZW patent (now expired), GIF reading
support has been removed altogether, and the GIF writer has been simplified
to produce "uncompressed GIFs".  This technique does not use the LZW
algorithm; the resulting GIF files are larger than usual, but are readable
by all standard GIF decoders.

We are required to state that
    "The Graphics Interchange Format(c) is the Copyright property of
    CompuServe Incorporated.  GIF(sm) is a Service Mark property of
    CompuServe Incorporated."
