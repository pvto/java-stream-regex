
package io.streamregex;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Scanner;
import org.junit.Test;

public class Alternatives {

    @Test
    public void scannerTest()
    {
        InputStream in = new ByteArrayInputStream("aabaaabba".getBytes());
        Scanner p = new Scanner(in)
                ;
        String a = p.findWithinHorizon("aab", 3),
                b = p.findWithinHorizon("aaa", 3),
                c = p.findWithinHorizon("bba", 3)
                ;
        
        in = new ByteArrayInputStream("aabaaabba".getBytes());
        p = new Scanner(in);
        System.out.println(p.findWithinHorizon("a*b", 0));
        System.out.println(p.findWithinHorizon("b+a", 0));  //bba, but we can't really skip
        System.out.println(p.findWithinHorizon("a{1,3}", 0));
        System.out.println(p.findWithinHorizon("a+b+a*", 0));
        
    }
}