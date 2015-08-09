package io;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** This caches bytes from the beginning, or from last mark() onwards.
 *  Can be rewinded as many times as necessary. */
public class CachingInputStream extends InputStream {
    
    public CachingInputStream(InputStream in) { this.in = in; }
    
    final private InputStream in;
    private int recovering = 0;
    private ByteArrayOutputStream cache = new ByteArrayOutputStream();
    private byte[] cached;
    
    @Override public int read() throws IOException
    {
        if (recovering > 0)
        {
            return recover();
        }
        int r = in.read();
        cache.write(r);
        return r;
    }
    
    public void mark()
    {
        if (recovering > 0)
        {
            cache = util.Arf.copyOfRange(cache, cache.size() - recovering, cache.size());
        }
        else
        {
            recovering = 0;
            cache.reset();
        }
    }
    
    public int rewind()
    {
        cached = cache.toByteArray();
        recovering = cached.length;
        return recovering;
    }
    
    private int recover()
    {
        return cached[cached.length - recovering--];
    }

    public int queueLength()
    {
        return recovering;
    }

}
