WireSpider
=====
[![Build Status](https://travis-ci.org/kazyx/wirespider.svg?branch=master)](https://travis-ci.org/kazyx/wirespider)
[![Coverage Status](https://coveralls.io/repos/kazyx/wirespider/badge.svg?branch=master)](https://coveralls.io/r/kazyx/wirespider)

WireSpider is a simple and compact WebSocket ([RFC6455](http://tools.ietf.org/html/rfc6455)) client written in Java.

- High performance NIO based implementation.
- Incredibly compact binary size.
- Android compatible. (Minimum API level 9 and Compilation target 19)

## Download

Download [JAR](https://bintray.com/kazyx/maven/net.kazyx%3Awirespider) from Bintray,
or add the following dependencies in your `build.gradle`.

```groovy
buildscript {
    repositories {
        jcenter()
    }
}

dependencies {
    compile 'net.kazyx:wirespider:1.0.1'
}
```

## How to build from source code
```bash
cd <root>/wirespider
./gradlew assemble
```
Now you can find `wirespider-x.y.z.jar` at `<root>/wirespider/build/libs`

## Usage

### Set-up
```java
Base64.encoder(new Base64.Encoder() {
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

URI uri = URI.create("ws://host:port/path");
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
    public void onClosed() {
        // WebSocket connection is closed.
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

### Gracefully close connection
```java
websocket.closeAsync();
// WebSocketConnection.onClosed() will be called soon.
```

### Release resources
```java
factory.destroy();
// Any async operations will do nothing since now.
```
