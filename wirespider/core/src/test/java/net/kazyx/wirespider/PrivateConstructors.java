/*
 * WireSpider
 *
 * Copyright (c) 2015 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.util.ArgumentCheck;
import net.kazyx.wirespider.util.Base64;
import net.kazyx.wirespider.util.BinaryUtil;
import net.kazyx.wirespider.util.HandshakeSecretUtil;
import net.kazyx.wirespider.util.IOUtil;
import net.kazyx.wirespider.util.SelectionKeyUtil;
import net.kazyx.wirespider.util.WsLog;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class PrivateConstructors {

    @Test
    public void log() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(WsLog.class);
    }

    @Test
    public void argumentCheck() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(ArgumentCheck.class);
    }

    @Test
    public void base64() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(Base64.class);
    }

    @Test
    public void binaryUtil() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(BinaryUtil.class);
    }

    @Test
    public void handshakeSecretUtil() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(HandshakeSecretUtil.class);
    }

    @Test
    public void ioUtil() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(IOUtil.class);
    }

    @Test
    public void opCode() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(OpCode.class);
    }

    @Test
    public void selectionKeyUtil() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(SelectionKeyUtil.class);
    }

    private void constructor(Class<?> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object obj = constructor.newInstance();
        assertThat(obj, is(notNullValue()));
        assertThat(obj, instanceOf(clazz));
    }
}
