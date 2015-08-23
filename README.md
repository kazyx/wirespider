WireSpider
=====
[![Build Status](https://travis-ci.org/kazyx/wirespider.svg?branch=master)](https://travis-ci.org/kazyx/wirespider)
[![Coverage Status](https://coveralls.io/repos/kazyx/wirespider/badge.svg?branch=master)](https://coveralls.io/r/kazyx/wirespider)

WireSpider is a simple and compact WebSocket ([RFC6455](http://tools.ietf.org/html/rfc6455)) client written in Java.

- High performance `java.nio` based implementation.
- Incredibly compact binary size.(Only 70KB!!)
- Android compatible. (Note that Java 7 language features must be enabled.)

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

## Build from source code
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

## ProGuard

No additional prevension required.

## License

This software is published under the [MIT License](LICENSE).
