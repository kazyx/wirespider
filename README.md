Apti WebSocket client
=====

Apti is a completely simple WebSocket client based on [RFC6455](http://tools.ietf.org/html/rfc6455).

Apti is written in Java 7 syntax.  
Also compatible with Android applications compiled with compilation target 19 or later.

## How to build
```bash
cd <apti-root>
./gradlew build
```

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
AsyncSource async = new AsyncSource(3);
// Specify size of fixed thread pool

WebSocketClientFactory factory = new WebSocketClientFactory(async);
// Various operations by instances created by this factory are invoked on thread resources in AsyncSource

URI uri = URI.create("ws://host:port/path");

Future<WebSocket> future = factory.open(uri, new WebSocketConnection() {
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

### Close connection
```java
websocket.closeAsync();
// WebSocketConnection.onClosed() will be called soon.
```

### Release thread resources
```java
async.destroy();
// Any async operations will throw RejectedExecutionException since now.
```
