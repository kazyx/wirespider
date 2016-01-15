package net.kazyx.wirespider.sampleapp.echoserver;

public class DummyServerManager implements LocalServerManager {
    @Override
    public void bootAsync(ServerLifeCycle listener) {
    }

    @Override
    public void shutdownAsync() {
    }

    @Override
    public boolean isRunning() {
        return false;
    }
}
