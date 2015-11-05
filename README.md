webrtcpeer-android
=================
This repository contains an Android library for creating WebRTC connections.

This project is part of [NUBOMEDIA](http://www.nubomedia.eu).

Repository structure
--------------------
This repository consists of an Android Studio library project. The project contains source code from [WebRTC software project](https://chromium.googlesource.com/external/webrtc/)
and the following third-party library:
* [https://github.com/nubomedia-vtt/utilities-android](https://github.com/nubomedia-vtt/utilities-android)

Building
--------
You can import this project to your own Android Studio project via Maven by adding the following line to module's `build.gradle` file:
```
compile 'fi.vtt.nubomedia:webrtcpeer-android:1.0.0'
```

If you want to build the project from source, you need to import the third-party library via Maven by adding the following line to
the module's `build.gradle` file
```
compile 'fi.vtt.nubomedia:utilities-android:1.0.0'
```

Licensing
---------
This repository is licensed under a BSD license. See the `LICENSE` file for more information.

Support
-------
Support is provided through the [NUBOMEDIA VTT Public Mailing List](https://groups.google.com/forum/#!forum/nubomedia-vtt).

