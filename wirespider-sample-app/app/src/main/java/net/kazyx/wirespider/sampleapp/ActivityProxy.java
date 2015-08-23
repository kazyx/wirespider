package net.kazyx.wirespider.sampleapp;

import net.kazyx.wirespider.sampleapp.echoserver.LocalServerManager;

public interface ActivityProxy {
    void onConnected();

    void onDisconnected();

    ClientManager getClientManager();

    LocalServerManager getLocalServerManager();
}
