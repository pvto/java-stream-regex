package io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import org.junit.Test;
import static org.junit.Assert.*;

public class CachingInputStreamTest {
    
    @Test
    public void testCaching() throws UnsupportedEncodingException, IOException
    {
        InputStream data = new ByteArrayInputStream("foobarbaz".getBytes("UTF-8"));
        CachingInputStream ci = new CachingInputStream(data);
        int c;
        int i = 0;
        while(ci.read() != 'r') i++;
        assertEquals(5, i);
        assertEquals(6, ci.rewind());
        i = 0;
        while(ci.read() != 'z') i++;
        assertEquals(8, i);
        assertEquals(9, ci.rewind());
        for(i = 0; i < 3; i++) ci.read();
        ci.mark();
        assertEquals('b', ci.read());
        for(i = 0; i < 3; i++) ci.read();
        ci.rewind();
        assertEquals('b', ci.read());
    }
    
}
