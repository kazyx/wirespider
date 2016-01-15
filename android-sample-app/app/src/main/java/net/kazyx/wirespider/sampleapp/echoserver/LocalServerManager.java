package net.kazyx.wirespider.sampleapp.echoserver;

public interface LocalServerManager {
    void bootAsync(ServerLifeCycle listener);

    void shutdownAsync();

    boolean isRunning();

    interface ServerLifeCycle {
        void onLaunched();

        void onStopped();
    }
}
