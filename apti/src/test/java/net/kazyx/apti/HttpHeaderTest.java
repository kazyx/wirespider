package net.kazyx.apti;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class HttpHeaderTest {
    @Test
    public void singleValue() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("value").build();
        assertThat(header.toHeaderLine(), is("name: value"));
    }

    @Test
    public void multipleValue() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("value1").appendValue("value2").build();
        assertThat(header.toHeaderLine(), is("name: value1,value2"));
    }

    @Test
    public void emptyValue() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("").build();
        assertThat(header.toHeaderLine(), is("name: "));
    }

    @Test
    public void emptyFirstValueIsSkipped() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("").appendValue("value2").build();
        assertThat(header.toHeaderLine(), is("name: value2"));
    }

    @Test
    public void emptyNonFirstValueIsSkipped() {
        HttpHeader header = new HttpHeader.Builder("name").appendValue("value1").appendValue("").appendValue("value2").build();
        assertThat(header.toHeaderLine(), is("name: value1,value2"));
    }

    @Test
    public void multipleCookie() {
        HttpHeader header = new HttpHeader.Builder("COOKIE")
                .appendValue("name1=value1").appendValue("name2=value2").build();
        assertThat(header.toHeaderLine(), is("COOKIE: name1=value1;name2=value2"));
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
