package net.kazyx.wirespider;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Base64Encoder implements Base64.Encoder {
    @Override
    public String encode(byte[] source) {
        try {
            Class<?> c = Class.forName("android.util.Base64");
            Method m = c.getDeclaredMethod("encodeToString", byte[].class, int.class);
            return (String) m.invoke(null, source, 2);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            // This is not android device;
        }

        try {
            Class<?> c = Class.forName("org.apache.commons.codec.binary.Base64");
            Method m = c.getDeclaredMethod("encodeBase64String", byte[].class);
            return (String) m.invoke(null, new Object[]{source});
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}