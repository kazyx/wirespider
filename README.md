Apti WebSocket client
=====

Apti is a simple and compact WebSocket([RFC6455](http://tools.ietf.org/html/rfc6455)) client written in Java.

- NIO based implementation.
- Android applications compatible. (Compilation target 19 or later)

## How to build
```bash
cd <apti-root>/master
./gradlew assemble
```
Now you can find `apti-x.y.z.jar` at `<apti-root>/apti/build/libs`

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

WebSocketClientFactory factory = new WebSocketClientFactory();
// Use this WebSocketClientFactory forever while your process alive.

URI uri = URI.create("ws://host:port/path");
Future<WebSocket> future = factory.openAsync(uri, new WebSocketConnection() {
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
});

WebSocket websocket = future.get(5, TimeUnit.SECONDS);
```

### Send message
```java
websocket.sendTextMessageAsync("Hello");
```
```java
websocket.sendBinaryMessageAsync(new byte[]{0x01, 0x02, 0x03, 0x04});
```

### Close connection
```java
websocket.closeAsync();
// WebSocketConnection.onClosed() will be called soon.
```

### Release resources
```java
factory.destroy();
// Any async operations will do nothing since now.
```
