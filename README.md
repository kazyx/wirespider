WireSpider
=====
[![Build Status](https://travis-ci.org/kazyx/wirespider.svg?branch=master)](https://travis-ci.org/kazyx/wirespider)
[![Coverage Status](https://coveralls.io/repos/kazyx/wirespider/badge.svg?branch=master)](https://coveralls.io/r/kazyx/wirespider)
[![Download](https://api.bintray.com/packages/kazyx/maven/net.kazyx%3Awirespider/images/download.svg)](https://bintray.com/kazyx/maven/net.kazyx%3Awirespider/_latestVersion)

WireSpider is a simple and compact WebSocket ([RFC6455](http://tools.ietf.org/html/rfc6455)) client written in Java.

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
    compile 'net.kazyx:wirespider:1.3.0'
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
```java
Base64.setEncoder(new Base64.Encoder() {
    @Override
    public String encode(byte[] source) {
        // Please use apache-commons or Android Base64 etc.
        // Required for opening handshake.
    }
});
```

### Open WebSocket connection
```java
WebSocketFactory factory = new WebSocketFactory();
// It is recommended to use this WebSocketFactory while your process is alive.

URI uri = URI.create("ws://host:port/path"); // ws scheme
SessionRequest req = new SessionRequest.Builder(uri, new WebSocketHandler() {
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
}).build();

WebSocket websocket = factory.openAsync(req).get(5, TimeUnit.SECONDS);
```

### Send messages
```java
websocket.sendTextMessageAsync("Hello");
```
```java
websocket.sendBinaryMessageAsync(new byte[]{0x01, 0x02, 0x03, 0x04});
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

```java
URI uri = URI.create("wss://host:port/path"); // wss scheme
SessionRequest req = new SessionRequest.Builder(uri, handler).build();

WebSocket websocket = factory.openAsync(req).get(5, TimeUnit.SECONDS); // This is a WebSocket over TLS
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

#### Per message deflate extension

[ ![Download](https://api.bintray.com/packages/kazyx/maven/net.kazyx%3Awirespider-permessage-deflate/images/download.svg) ](https://bintray.com/kazyx/maven/net.kazyx%3Awirespider-permessage-deflate/_latestVersion)

Permessage deflate extension is provided by `wirespider-permessage-deflate` placed under `wirespider/permessage-deflate`.  
Downloaded [JAR](https://bintray.com/kazyx/maven/net.kazyx%3Awirespider-permessage-deflate)
or write Gradle dependency as follows.

```groovy
dependencies {
    compile 'net.kazyx:wirespider-permessage-deflate:1.3.0'
}
```

Set `DeflateRequest` in the `SessionRequest`.

```java
List<ExtensionRequest> extensions = new ArrayList<>();
extensions.add(new DeflateRequest.Builder().setStrategy(new CompressionStrategy() {
    @Override
    public int minSizeInBytes() {
        return 200; // Threshold message size to perform compression.
    }
}).build());

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
