/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider.test.rfc6455;

import net.kazyx.wirespider.FrameRx;
import net.kazyx.wirespider.FrameTx;
import net.kazyx.wirespider.Handshake;
import net.kazyx.wirespider.SocketChannelWriter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

class PackageBreaker {
    static FrameRx newRfc6455Rx(FrameRx.Listener listener, int maxPayload, boolean isClient) {
        try {
            Class<?> clazz = Class.forName("net.kazyx.wirespider.rfc6455.Rfc6455Rx");
            Constructor<?> constructor = clazz.getDeclaredConstructor(FrameRx.Listener.class, int.class, boolean.class);
            constructor.setAccessible(true);
            return (FrameRx) constructor.newInstance(listener, maxPayload, isClient);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static FrameTx newRfc6455Tx(SocketChannelWriter writer, boolean isClient) {
        try {
            Class<?> clazz = Class.forName("net.kazyx.wirespider.rfc6455.Rfc6455Tx");
            Constructor<?> constructor = clazz.getDeclaredConstructor(SocketChannelWriter.class, boolean.class);
            constructor.setAccessible(true);
            return (FrameTx) constructor.newInstance(writer, isClient);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    static Handshake newRfc6455Handshake(SocketChannelWriter writer, boolean isClient) {
        try {
            Class<?> clazz = Class.forName("net.kazyx.wirespider.rfc6455.Rfc6455Handshake");
            Constructor<?> constructor = clazz.getDeclaredConstructor(SocketChannelWriter.class, boolean.class);
            constructor.setAccessible(true);
            return (Handshake) constructor.newInstance(writer, isClient);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
