package net.kazyx.apti;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class PrivateConstructors {

    @Test
    public void aptiLog() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(AptiLog.class);
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
    public void bitMask() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(BitMask.class);
    }

    @Test
    public void byteArrayUtil() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(ByteArrayUtil.class);
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
    public void randomSource() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(RandomSource.class);
    }

    @Test
    public void textUtil() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        constructor(TextUtil.class);
    }

    private void constructor(Class<?> clazz) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object obj = constructor.newInstance();
        assertThat(obj, is(notNullValue()));
        assertThat(obj, instanceOf(clazz));
    }
}
