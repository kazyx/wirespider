package net.kazyx.apti;

import org.junit.Assert;
import org.junit.Test;

public class HttpHeaderTest {
    @Test
    public void singleValue() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("value").build();
        Assert.assertEquals("name: value", header.toHeaderLine());
    }

    @Test
    public void multipleValue() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("value1").appendValue("value2").build();
        Assert.assertEquals("name: value1,value2", header.toHeaderLine());
    }

    @Test
    public void emptyValue() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("").build();
        Assert.assertEquals("name: ", header.toHeaderLine());
    }

    @Test
    public void emptyFirstValueIsSkipped() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("").appendValue("value2").build();
        Assert.assertEquals("name: value2", header.toHeaderLine());
    }

    @Test
    public void emptyNonFirstValueIsSkipped() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("value1").appendValue("").appendValue("value2").build();
        Assert.assertEquals("name: value1,value2", header.toHeaderLine());
    }

    @Test
    public void multipleCookie() {
        HttpHeader header = new HttpHeader.Builder("COOKIE")
                .appendValue("name1=value1").appendValue("name2=value2").build();
        Assert.assertEquals("COOKIE: name1=value1;name2=value2", header.toHeaderLine());
    }

    @Test(expected = NullPointerException.class)
    public void builderRejectNullName() {
        new HttpHeader.Builder(null);
    }

    @Test(expected = NullPointerException.class)
    public void builderRejectNullValue() {
        new HttpHeader.Builder("name").appendValue(null);
    }
}
