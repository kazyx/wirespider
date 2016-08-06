WireSpider
=====
[![Build Status](https://travis-ci.org/kazyx/wirespider.svg?branch=master)](https://travis-ci.org/kazyx/wirespider)
[![Coverage Status](https://coveralls.io/repos/kazyx/wirespider/badge.svg?branch=master)](https://coveralls.io/r/kazyx/wirespider)
[![Download](https://api.bintray.com/packages/kazyx/maven/net.kazyx%3Awirespider/images/download.svg)](https://bintray.com/kazyx/maven/net.kazyx%3Awirespider/_latestVersion)

WireSpider is a simple and compact WebSocket ([RFC6455](https://tools.ietf.org/html/rfc6455)) client written in Java.

- High performance `java.nio` based implementation.
- Incredibly compact binary size.
- Android compatible. (Note that Java 7 language features must be enabled.)

## Download

Download [JAR](https://bintray.com/kazyx/maven/net.kazyx%3Awirespider) from Bintray,
or write Gradle dependency as follows.

```groovy
buildscript {
    repositories {
        jcenter()
    }
}

dependencies {
    compile 'net.kazyx:wirespider:1.3.1'
}
```

## Build from source code
```bash
cd <root>/wirespider
./gradlew wirespider:assemble
```
Now you can find `wirespider-x.y.z.jar` at `<root>/wirespider/core/build/libs`

## Usage

### Set-up

Set external Base64 conversion function at first.
The following snippets are the samples for Java 8 and Android.

**Java 8**
```java
Base64.setEncoder(source -> java.util.Base64.getEncoder().encodeToString(source));
```

**Android**
```java
Base64.setEncoder(source -> android.util.Base64.encodeToString(source, android.util.Base64.DEFAULT));
```

### Open WebSocket connection
```java
WebSocketFactory factory = new WebSocketFactory();
// It is recommended to use this WebSocketFactory while your process is alive.

URI uri = URI.create("ws://host:port/path"); // ws scheme
WebSocketHandler handler = new WebSocketHandler() {
    @Override
    public void onTextMessage(String message) {
        // Received text message.
    }

    @Override
    public void onBinaryMessage(byte[] message) {
        // Received binary message.
    }

    @Override
    public void onClosed(int code, String reason) {
        // Connection is closed.
    }
};
```

**Blocking style**
```
SessionRequest req = new SessionRequest.Builder(uri, handler)
        .setConnectionTimeout(5, TimeUnit.SECONDS)
        .build();

WebSocket websocket = factory.open(req);
```

**Async style**
```
SessionRequest req = new SessionRequest.Builder(uri, handler)
        .build();

Future<WebSocket> futureWebSocket = factory.openAsync(req);
```

### Send messages
```java
websocket.sendTextMessageAsync("Hello");

websocket.sendBinaryMessageAsync(new byte[]{0x01, 0x02, 0x03, 0x04});
```

### Send partial messages

```java
try (PartialMessageWriter writer = websocket.newPartialMessageWriter()) {
    writer.sendPartialFrameAsync("H", false);
    writer.sendPartialFrameAsync("e", false);
    writer.sendPartialFrameAsync("llo", true/*isFinal*/);
} // Don't forget to close PartialMessageWriter
```

### Close connection
```java
websocket.closeAsync();
// WebSocketHandler.onClosed() will be called soon.
```

### Release resources
```java
factory.destroy();
```

### WebSocket over TLS

Use `URI` created with `wss` scheme instead of `ws`.

```java
URI uri = URI.create("wss://host:port/path");
```

#### TLSv1.1 and TLSv1.2 on JDK7

Use `SSLContext` on which the newer version of TLS is enabled,
since JDK7 disables client side `TLSv1.1` and `TLSv1.2` by default.

```java
SSLContext context = SSLContext.getInstance("TLSv1.1");
context.init(null, null, null);

WebSocketFactory.setSslContext(context);
```

#### TLSv1.1 and over on Android 4.4 and lower versions

It is recommended to use Google Play services to enable `TLSv1.1` and over.

```groovy
dependencies {
    compile 'com.google.android.gms:play-services-basement:+'
}
```

```java
ProviderInstaller.installIfNeeded(getApplicationContext());
```

### Extensions

WebSocket extensions can be implemented with `net.kazyx.wirespider.extension.Extension` interface.

#### Per-Message Deflate extension

[ ![Download](https://api.bintray.com/packages/kazyx/maven/net.kazyx%3Awirespider-pmdeflate/images/download.svg) ](https://bintray.com/kazyx/maven/net.kazyx%3Awirespider-pmdeflate/_latestVersion)

Per-Message Deflate extension ([RFC7692](https://tools.ietf.org/html/rfc7692)) is provided by `wirespider-pmdeflate` placed under `wirespider/permessage-deflate`.  
Download [JAR](https://bintray.com/kazyx/maven/net.kazyx%3Awirespider-pmdeflate)
or write Gradle dependency as follows.

```groovy
dependencies {
    compile 'net.kazyx:wirespider-pmdeflate:1.3.1'
}
```

Set `DeflateRequest` into the `SessionRequest`.

```java
ExtensionRequest deflate = new DeflateRequest.Builder()
        .setCompressionThreshold(100)
        .build();
List<ExtensionRequest> extensions = new ArrayList<>();
extensions.add(deflate);

SessionRequest req = new SessionRequest.Builder(uri, handler)
        .setExtensions(extensions)
        .build();
```

## ProGuard

No additional prevension required.

## Contribution

Contribution for bugfix, performance improvement, API refinement and extension implementation are welcome.

Note that JUnit test code is required for the pull request.

## License

This software is released under the [MIT License](LICENSE).
