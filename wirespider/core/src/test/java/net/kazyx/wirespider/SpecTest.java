/*
 * WireSpider
 *
 * Copyright (c) 2016 kazyx
 *
 * This software is released under the MIT License.
 * http://opensource.org/licenses/mit-license.php
 */

package net.kazyx.wirespider;

import net.kazyx.wirespider.rfc6455.Rfc6455;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class SpecTest {
    WebSocketFactory factory;

    @Before
    public void setup() throws IOException {
        factory = new WebSocketFactory();
    }

    @After
    public void teardown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @Test(expected = NullPointerException.class)
    public void nullSpec() throws IOException {
        factory.setSpec(null);
    }

    @Test
    public void setRfc6455() {
        factory.setSpec(new Rfc6455());
    }
}
