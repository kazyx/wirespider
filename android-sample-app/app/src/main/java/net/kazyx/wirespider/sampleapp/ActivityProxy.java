/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.sampleapp;

public interface ActivityProxy {
    void onConnected();

    void onDisconnected();

    ClientManager getClientManager();
}
