
package io;

import java.io.IOException;
import java.io.InputStream;

public class FeatureInputStream extends InputStream {

    final private InputStream in;
    private int buffer = -1;
    private int line = 1,
            col = 1;

    
    public FeatureInputStream(InputStream in)
    {
        this.in = in;
        mark();
    }
    
    protected io.CachingInputStream getBackingCachingInputStream()
    {
        return util.RT.requireClass(this, in, io.CachingInputStream.class);
    }
    private int[] markPos = null;
    int markBuffer = -1;
    /** shorthand that assumes that this decorates a CachingInputStream */
    public void mark()
    {
        markPos = snapPos();
        markBuffer = buffer;
        getBackingCachingInputStream().mark();
    }
    /** shorthand that assumes that this decorates a CachingInputStream */
    public int rewind()
    {
        line = markPos[0];  col = markPos[1];
        buffer = markBuffer;
        return getBackingCachingInputStream().rewind();
    }

    
    @Override
    public void close() throws IOException
    {
        in.close();
    }


    @Override
    public int read() throws IOException
    {
        if (buffer != -1)
        {
            int x = buffer;
            buffer = -1;
            return x;
        }
        return in.read();
    }

    public void push(int byt)
    {
        if (buffer != -1)
            throw new RuntimeException("fin buffer overflow");
        buffer = byt;
    }
    
    private void updLc(int ch) {updLc(ch,1);}
    private void updLc(int ch, int dir)
    {
        if      (ch == '\n')    { line += dir;  col = dir>0?1:100; }
        else if (ch != '\r')    { col += dir; }
    }
    
    public void skipDelims() throws IOException
    {
        int ch = read();
        while(ch == 0x20 || ch == '\r' || ch == '\n' || ch == '\t' || ch == ',')
        {
            updLc(ch);
            ch = read();
        }
        buffer = ch;
    }

    public int readUtf8() throws IOException
    {
        int x = read();
        if (x < 192)
        {
            updLc(x);
            return x;
        }
        int ch = read();
        if ((ch & 0xC0) == 0x80)
        { // unicode multi-byte character
            int ff = x & 0xE0;
            if (ff == 0xE0)
            { // three-byte char
                x = ((x & 0x0F) << 6) | (ch & 0x3F);
                ch = read();
            } 
            else if (ff == 0xC0)
            { // two-byte char
                x = (x & 0x1F);
            }
            x = (x << 6) | (ch & 0x3F);
            updLc(x);
            return x;
        }
        else
        { // it wasn't utf-8 after all, assume ISO-8859-1
            buffer = ch;
            updLc(x);
            return x;
        }
    }


    
    public char readChar() throws IOException
    {
        int ch = readUtf8();
        return (char)ch;
    }

    public char[] readCharArray(int n) throws IOException
    {
        char[] r = new char[n];
        for(int i = 0; i < n; i++)
        {
            char ch = readChar();
            if (ch == 0xFFFF)
                break;
            r[i] = ch;
        }
        return r;
    }

    public int peekChar() throws IOException
    {
        if (buffer != -1)
            return (char)buffer;
        int[] pos = snapPos();
        buffer = readUtf8();
        line = pos[0];  col = pos[1];
        return buffer;
    }

    public String readSpan(char a, char o) throws IOException
    {
        StringBuilder b = new StringBuilder();
        char ch = readChar();
        while(ch != a) ch = readChar();
        for(;;)
        {
            ch = readChar();
            if (ch == o)
                break;
            b.append(ch);
        }
        return b.toString();
    }

    public String readMarked() throws IOException
    {
        CachingInputStream cin = getBackingCachingInputStream();
        StringBuilder b = new StringBuilder();
        cin.rewind();
        while(cin.queueLength() > 0)
        {
            b.append((char)readUtf8());
        }
        return b.toString();
    }

    
    public int getLine() { return line; }
    public int getCol() { return col; }
    public int[] snapPos() { return new int[]{line,col}; }

}
