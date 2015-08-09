package util;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import jdk.nashorn.internal.runtime.regexp.joni.Syntax;

public class Arf 

{

    static public Object[]  objs(Object... o) { return o; }
    static public Object[]  Nobjs(int i) 
    {
        return new Object[i];
    }
    static public Object[]  concato(Object[] a, Object[] b) 
    {
        Object[] c = new Object[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    static public int[]     ints(int... I) 
    {
        return I;
    }


    static public ByteArrayOutputStream copyOfRange(ByteArrayOutputStream cache, int start, int end) 
    {
        byte[] chunk = Arrays.copyOfRange(cache.toByteArray(), start, end);
        ByteArrayOutputStream ret = new ByteArrayOutputStream();
        try 
        {
            ret.write(chunk);
        }
        catch(Exception foo)
        { 
            throw new RuntimeException("Can't append bytes", foo);
        }
        return ret;
    }

    static public int       count(String s, char c) 
    {
        int count = 0;
        for(int i = 0; i < s.length(); i++) 
        {
            if(c == s.charAt(i)) {count++;}
        }
        return count;
    }

    static public void      insertAtNextNull(Syntax[] structure, Syntax x) 
    {
        for(int i = 0; i < structure.length; i++)
        {
            if (structure[i] == null) 
            {
                structure[i] = x;  return;
            }
        }
        throw new IndexOutOfBoundsException("array is full, can't insert " + x);
    }

    static public boolean   within(char y, char... cc) 
    {
        for(char c : cc) if (y == c) {return true;}
        return false;
    }

    static public char[]    repeat(char c, int n) 
    {
        char[] ret = new char[n];
        for(int i = 0; i < n; i++)
             ret[i] = c;
        return ret;
    }

    static public int[]     copyOf(int[] A) 
    {
        return Arrays.copyOf(A, A.length);
    }


}
