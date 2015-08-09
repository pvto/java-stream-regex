package io.streamregex;

import io.FeatureInputStream;
import java.io.IOException;
import org.junit.Test;
import static util.RT.fin;
import java.io.InputStream;
import static org.junit.Assert.*;
import org.junit.Ignore;

public class StreamRegexTest {
    
    @Test public void testSimple() throws IOException {         assertTrue(r("foobar").matches(fin("foobar"))); }
    @Test public void testMatch() throws IOException {          assertFalse(r("foo").matches(fin("foo-x"))); }
    @Test public void testUtf8() throws IOException {           assertTrue(r("[\\u0020-\\u0080]*").matches(fin("foobar"))); }
    @Test public void testSpecials() throws IOException {       assertTrue(r("a\\[\\]\\]\\(\\)b[\\[\\-]+").matches(fin("a[]]()b-["))); }
    @Test public void testRepet() throws IOException {          assertTrue(r("fo*ba*r").matches(fin("foobr"))); }
    @Test public void testRepeti() throws IOException {         assertTrue(r("f?o?bar").matches(fin("fbar"))); }
    @Test public void testRepete() throws IOException {         assertTrue(r("fo+o?bar+").matches(fin("foobarr"))); }
    @Test public void testRepet3() throws IOException {         assertTrue(r("fo+bar").matches(fin("fooobar"))); }
    @Test public void testRepet2() throws IOException {         assertTrue(r("fo{2}bar").matches(fin("foobar"))); }
    @Test public void testRepet1_3() throws IOException {       assertTrue(r("fo{1,3}bar").matches(fin("fooobar"))); }
    @Test public void testRepet_3() throws IOException {        assertTrue(r("fo{,3}bar").matches(fin("fooobar"))); }
    @Test public void testRepet3_() throws IOException {        assertTrue(r("fo{3,}bar").matches(fin("fooobar"))); }
    @Test public void testRepetHigh() throws IOException {      assertFalse(r("fo{,2}bar").matches(fin("fooobar"))); }
    @Test public void testRepetLow() throws IOException {       assertFalse(r("fo{4,}bar").matches(fin("fooobar"))); }
    @Test public void testCharClass() throws IOException {      assertTrue(r("f[ob]+[ax\\t]?[r]").matches(fin("fooobar"))); }
    @Test public void testCharClass2() throws IOException {     assertTrue(r("[a-z^c-e]+").matches(fin("fooobar"))); }
    @Test public void testCharClass3() throws IOException {     assertTrue(r("[^c-e]+").matches(fin("fooobar"))); }
    @Test public void testCharClass4() throws IOException {     assertFalse(r("[^c-er]+").matches(fin("fooobar"))); }
    @Test public void testPipe1() throws IOException {          assertTrue(r("a|b*|c").matches(fin("a"))); }
    @Test public void testPipe1b() throws IOException {         assertTrue(r("a|b*|c").matches(fin("bb"))); }
    @Test public void testPipe1c() throws IOException {         assertTrue(r("a|b*|c").matches(fin("c"))); }
    @Test public void testParen() throws IOException {          assertTrue(r("(a)|(b*)|(c)").matches(fin("c"))); }
    @Test public void testGroup1() throws IOException {         assertTrue(r("(f)(o)oobar").matches(fin("fooobar"))); }
    @Test public void testGroupRep() throws IOException {       assertTrue(r("(o)*").matches(fin("ooo"))); }
    @Test public void testGroupRep2() throws IOException {      assertFalse(r("(o){2}").matches(fin("ooo"))); }
    @Test public void testGroupRep3() throws IOException {      assertTrue(r("(o){3}").matches(fin("ooo"))); }
    @Test public void testGroupRepRep() throws IOException {    assertTrue(r("(o)*(p)*").matches(fin("ooopp"))); }
    @Test public void testGroupPipe() throws IOException {      assertTrue(r("(f|o)*bar").matches(fin("fooobar"))); }
    @Test public void testGroupPipe2() throws IOException {     assertTrue(r("(f|(o))*bar").matches(fin("fooobar"))); }
    @Test public void testGroupPipe2_() throws IOException {    assertTrue(r("((f)|(o))*bar").matches(fin("fooobar"))); }
    @Test public void testGroupPipe3() throws IOException {     assertTrue(r("(((fg?))|(o|p))*(bar)+").matches(fin("fooobarbar"))); }
    @Test public void testGroup2b() throws IOException {        
        StreamRegex r = r("(f(o?)?o)*");
        assertTrue(r.root.GROUP);
        assertEquals(0, r.root.any.get(0).min);
        assertEquals(Integer.MAX_VALUE, r.root.any.get(0).max);
        assertEquals(1, r.root.any.size());
        assertTrue(r.matches(fin("foo")));
    }
    @Test public void testGroup3() throws IOException {         assertTrue(r("(f|[op]{1})*bar").matches(fin("fooobar"))); }
    @Test public void testGroupCc() throws IOException {        assertTrue(r("(f[op]{1,3})*").matches(fin("fooofoo"))); }
    @Test public void testGroupNest3() throws IOException {     assertTrue(r("(((a)))*").matches(fin("aaa"))); }
    @Test public void testGroupNest32() throws IOException {    assertTrue(r("(((a)))*((b)){2}").matches(fin("aaabb"))); }
    @Test public void testGroupNest3ab() throws IOException {   assertTrue(r("(((a)b))*").matches(fin("abab"))); }
    @Test public void testGroupNest3a_b() throws IOException {  assertTrue(r("((a(b)))*").matches(fin("abab"))); }
    @Test public void testGroupNest() throws IOException {      assertTrue(r("(b(ö|a(c)?(r|x)))").matches(fin("bar"))); }
    @Test public void testGroup5() throws IOException {         assertFalse(r("(f[op]{3})*(b(c|(b)(r|x)))").matches(fin("fooobar"))); }
    @Test public void testCombo() throws IOException {
        StreamRegex r = r("(a*[0-9]+(.[0-9])?)|foobar|baz");
        assertTrue(r.matches(fin("aaaa0.3")));
        assertTrue(r.matches(fin("foobar")));
        assertTrue(r.matches(fin("baz")));
    }
    @Test public void testSimplify() {
        CharClass x = new CharClass();
        x.inr.add(new CharRange(100, 200));
        x.outr.add(new CharRange(110,111));
        x.simplify();
        assertTrue(!x.in(0));
        assertTrue(!x.in((int)'\uFFFF'));
        assertTrue(!x.in(99));
        assertTrue(x.in(100));
        assertTrue(x.in(109));
        assertTrue(!x.in(110));
        assertTrue(!x.in(111));
        assertTrue(x.in(112));
        assertTrue(x.in(200));
    }
    
    private StreamRegex r(String s) {
        return new StreamRegex(s);
    }
    private StreamRegex rp(String s) {
        StreamRegex x = r(s);
        SRNode.print(System.out, x.root);
        return x;
    }
    
    @Test public void testBig() throws IOException {
        testBig_(1000000);
    }
    @Ignore @Test public void testBig2() throws IOException {
        testBig_(100000000);
    }
    private void testBig_(final int count) throws IOException {
        long start = System.currentTimeMillis();
        InputStream in = new InputStream() {
            int i = 0;
            @Override public int read() throws IOException {
                if (i++ > count) {return -1;}
                return 'a'+(i%2);
            }
        };
        assertTrue(r("(ab)*").matches(fin(in)));
        System.out.println("testBig(" + count + ") took " + (System.currentTimeMillis() - start) + " ms");
    }
    //benchmark(1000000): 190ms
    //         (10000000): 876ms
    //         (100000000): 7477ms
    //for reference, benchmark from a simple if-then automaton(100000000): 228ms
    
    @Test public void testRead() throws IOException {       assertEquals("foo", r("[fo]+").readItem(fin("foobar"))); }
    @Test public void testReadG() throws IOException {      assertEquals("foo", r("(foo|b00)").readItem(fin("foobar"))); }
    @Test public void testReadG2() throws IOException {     assertEquals("foo", r("(f[0-9]+|foo|b00)").readItem(fin("foobar"))); }
    @Test public void testRead2() throws IOException {     
        FeatureInputStream fin = fin("foobarar");
        assertEquals("foo", r("(f[0-9]+|foo|b00)").readItem(fin));
        assertEquals("barar", r("b[ar]+").readItem(fin));
        assertEquals(null, r("[a-z]+").readItem(fin));
    }
    @Test public void testReadStop() throws IOException {     
        FeatureInputStream fin = fin("foobarbaz");
        assertEquals("foo", r("fo*c*").readItem(fin));
        assertEquals("bar", r("b[ax]+r*").readItem(fin));
        assertEquals("ba",  r("ba+").readItem(fin));
    }
    @Test public void testReadNot() throws IOException {     
        FeatureInputStream fin = fin("foobe");
        assertEquals(null, r("fo*c").readItem(fin));
        fin.rewind();
        assertEquals("foo", r("foo+").readItem(fin));
        assertEquals(null, r("az").readItem(fin));
        assertEquals("b", r("[a-d]*").readItem(fin));
        assertEquals("e", r("[^f]+").readItem(fin));
    }

    @Test public void testRead_aabbba() throws IOException {     
        FeatureInputStream fin = fin("aabbba");
        assertEquals("a", r("a").readItem(fin));
        assertEquals("a", r("a").readItem(fin));
        fin.mark();
        assertEquals(null, r("a").readItem(fin));
        fin.rewind();
        assertEquals("b", r("b").readItem(fin));
        assertEquals("b", r("b").readItem(fin));
        assertEquals("b", r("b").readItem(fin));
        fin.mark();
        assertEquals(null, r("b").readItem(fin));
        fin.rewind();
        assertEquals("a", r("a").readItem(fin));
        assertEquals(null, r("[a-z]+").readItem(fin));
    }
    
    @Test public void testCacheCharClasses() throws IOException {
        assertEquals(2, r("[o][m][o][m]").charClassCache.size());
        assertEquals(1, r("[abc][a-c]").charClassCache.size());
    }

    @Test public void testNestingLevel() throws IOException {
        StreamRegex st = r("abc");
        SRNode.print(System.out, st.root);
        assertEquals(3, st.root.any.get(0).next.next.nestingLevel);
        assertEquals(st.root, st.root.any.get(0).next.next.ancestorByRootDistance(0));
        assertEquals(st.root.any.get(0), st.root.any.get(0).next.next.ancestorByRootDistance(1));
        assertEquals(st.root.any.get(0).next, st.root.any.get(0).next.next.ancestorByRootDistance(2));
    } 
}
