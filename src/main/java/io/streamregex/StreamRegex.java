package io.streamregex;

import io.FeatureInputStream;
import util.SimpleHashCache;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import util.RT;

/**
 * A regex for matching streams and for reading tokens from streams.
 *
 * This implements a subset of java regular expressions (java.util.regex.*),
 * with the following simplifications.
 *
 * - reads UTF-8 / Latin1 stream - Only eager matching is supported - Character
 * representations: unicode characters \\uxxxx are implemented, while octal
 * characters are not - Back referencing constructs like ^ and \b and
 * (?&lt;=foo) are not supported - Startline and endline matchers ^ and $ are
 * not supported (while \\r \\n are) - Capturing groups are not implemented -
 * Character class shortcuts \w, \s, \d, etc. are not implemented - Supported
 * short escape characters are currently \\r \\n \\t - Regex comments are not
 * supported
 *
 * If you have other requirements, you could have a look at Streamflyer
 * (https://code.google.com/p/streamflyer/).
 */
public class StreamRegex {

    public final SRNode root = new SRNode(null, null, 0);
    public final String pattern;
    public SRNode lastMatchingFragment;

    public int getLastMatchingFragmentOffset()
    {
        return lastMatchingFragment.ancestorByRootDistance(1).offset;
    }
    final SimpleHashCache<CharClass> charClassCache = new SimpleHashCache();

    public StreamRegex(String pattern)
    {
        this.pattern = "(" + pattern + ")";
        readPipe(root, new int[] {
                0, 0
            });    // {pattern-pos, group-count}
        preprocess();
    }

    /**
     * this will hungrily match as much as possible - it will return null on a
     * partial match and never backtrack!
     */
    public String readItem(FeatureInputStream in) throws IOException
    {
        StringBuilder b = new StringBuilder();
        List<Matcher> lanes = matchers(root, new Matcher(root));
        boolean prevPossiblyEnding = false;
        for (int y = 0;; y++)
        {
            int c = in.readUtf8();
            if (c == -1)
            {
                break;
            }
            for (int i = lanes.size() - 1; i >= 0; i--)
            {
                Matcher m = lanes.get(i);
                m.ok = m.node.x != null && m.node.x.in(c);
                if (m.ok)
                {
                    if (m.incMatchedCount(y) > m.node.max)
                    {
                        m.ok = false;
                    }
                }
                else
                {
                    if (m.node.GROUP)
                    {
                        addChildren(c, matchers(m.node, m), lanes, y);
                        if (m.node.next != null && m.node.min <= m.matchedCount && m.matchedCount <= m.node.max)
                        {
                            addChildren(c, matcher(m.node.next, m), lanes, y);
                        }
                    }
                    else if (m.node.min <= m.matchedCount && m.matchedCount <= m.node.max)
                    {
                        if (m.node.next == null)
                        {
                            Matcher prev = backToGroup(m);
                            if (prev != null)
                            {
                                if (prev.incMatchedCount(y) < prev.node.max)
                                {
                                    prev.ok = true;
                                    lanes.add(prev);
                                }
                            }
                        }
                        else
                        {
                            addChildren(c, matchers(m.node, m), lanes, y);
                        }
                    }
                }
            }
            if (!pruneLanes(lanes))
            {
                in.push(c);
                if (in.peekChar() != -1 && !prevPossiblyEnding)
                {
                    return null;
                }
                break;
            }
            b.append((char) c);
            boolean possiblyEnding = false;
            for (Matcher lane : lanes)
            {
                possiblyEnding |= lane.node.POSSIBLY_ENDING_GROUP;
            }
            if (!recycleLanes(y, lanes))
            {
                if (in.peekChar() != -1 && !possiblyEnding)
                {
                    return null;
                }
                break;
            }
            prevPossiblyEnding = possiblyEnding;
        }
        return b.length() > 0 ? b.toString() : null;
    }

    public boolean matches(FeatureInputStream in) throws IOException
    {
        return matches(in, root);
    }

    private boolean matches(FeatureInputStream in, SRNode start) throws IOException
    {
        List<Matcher> lanes = matchers(start, new Matcher(start));
        for (int y = 0;; y++)
        {
            int c = in.readUtf8();
            if (c == -1)
            {
                return true;
            }
            for (int i = lanes.size() - 1; i >= 0; i--)
            {
                Matcher m = lanes.get(i);
                m.ok = m.node.x != null && m.node.x.in(c);
                if (m.ok)
                {
                    if (m.incMatchedCount(y) > m.node.max)
                    {
                        m.ok = false;
                    }
                }
                else
                {
                    if (m.node.GROUP)
                    {
                        addChildren(c, matchers(m.node, m), lanes, y);
                        if (m.node.next != null && m.node.min <= m.matchedCount && m.matchedCount <= m.node.max)
                        {
                            addChildren(c, matcher(m.node.next, m), lanes, y);
                        }
                    }
                    else if (m.node.min <= m.matchedCount && m.matchedCount <= m.node.max)
                    {
                        if (m.node.next == null)
                        {
                            Matcher prev = backToGroup(m);
                            if (prev != null)
                            {
                                if (prev.incMatchedCount(y) < prev.node.max)
                                {
                                    prev.ok = true;
                                    lanes.add(prev);
                                }
                            }
                        }
                        else
                        {
                            addChildren(c, matchers(m.node, m), lanes, y);
                        }
                    }
                }
            }
            if (!pruneLanes(lanes))
            {
                return false;
            }
            if (!recycleLanes(y, lanes))
            {
                return in.peekChar() == -1;
            }
        }
    }

    Matcher backToGroup(Matcher m)
    {
        m = m.prev;
        while (m != null)
        {
            if (m.node.GROUP)
            {
                return m;
            }
            m = m.prev;
        }
        return null;
    }

    private void addChildren(int c, List<Matcher> addendum, List<Matcher> lanes, int y)
    {
        for (int j = addendum.size() - 1; j >= 0; j--)
        {
            Matcher p = addendum.get(j);
            if (p.node.x != null && p.node.x.in(c))
            {
                lanes.add(p);
                p.incMatchedCount(y);
            }
            else
            {
                addChildren(c, matchers(p.node, p), lanes, y);
            }
        }
    }

    private boolean pruneLanes(List<Matcher> lanes)
    {
        boolean ok = false;
        for (int i = lanes.size() - 1; i >= 0; i--)
        {
            Matcher m = lanes.get(i);
            if (m.ok)
            {
                ok = true;
                lastMatchingFragment = m.node;
            }
            else
            {
                lanes.remove(i);
            }
        }
        return ok;
    }

    private boolean recycleLanes(int y, List<Matcher> lanes)
    {
        for (int i = lanes.size() - 1; i >= 0; i--)
        {
            Matcher m = lanes.get(i);
            if (m.matchedCount == m.node.max)
            {
                boolean replaced = false;
                SRNode next = m.node.next != null ? m.node.next : m.next;
                if (next != null)
                {
                    lanes.set(i, new Matcher(next).prev_(m));
                    replaced = true;
                }
                if (m.node.POSSIBLY_ENDING_GROUP && m.node.grouppar != null)
                {
                    Matcher g = m;
                    while ((g = backToGroup(g)) != null)
                    {
                        if (g.incMatchedCount(y) < g.node.max)
                        {
                            if (!replaced)
                            {
                                lanes.set(i, g);
                                replaced = true;
                            }
                            else
                            {
                                lanes.add(g);
                            }
                            g.ok = true;
                        }
                    }
                }
                if (!replaced)
                {
                    lanes.remove(i);
                }
            }
        }
        return lanes.size() > 0;
    }

    private List<Matcher> matchers(SRNode start, Matcher prev)
    {
        return start.GROUP ? matchers(start.any, start.next, prev) : matcher(start.next, prev);
    }

    private List<Matcher> matchers(List<SRNode> ss, SRNode next, Matcher prev)
    {
        List<Matcher> ret = new ArrayList<>();
        if (ss != null)
        {
            for (SRNode s : ss)
            {
                ret.add(new Matcher(s).prev_(prev));
            }
            for (Matcher m : ret)
            {
                m.next = next;
            }
        }
        return ret;
    }

    private List<Matcher> matcher(SRNode s, Matcher prev)
    {
        List<Matcher> ret = new ArrayList<>();
        if (s != null)
        {
            Matcher m = new Matcher(s).prev_(prev);
            m.next = m.node.next;
            ret.add(m);
        }
        return ret;
    }

    private boolean throwEx(String msg, int[] ii)
    {
        throw new RuntimeException(msg + "  at  " + pattern + "  [" + ii[0] + "]" + "  starting with  " + pattern.substring(ii[0]));
    }

    private boolean readPipe(SRNode x, int[] ii)
    {  // foo|bar|(baz)
        while (ii[0] < pattern.length())
        {
            readSeq(x, ii);
            if (!readChar('|', ii, NOOP))
            {
                break;
            }
        }
        return true;
    }

    private boolean readSeq(SRNode x, int[] ii)
    {    // fo[o0](bar){0,1}
        while (ii[0] < pattern.length())
        {
            if (peekChar(')', ii))
            {
                return true;
            }
            else if (peekChar('|', ii))
            {
                return true;
            }
            boolean ok
                    = (readChar(']', ii, NOOP) && throwEx("unmatched ]", ii))
                    || readGroup(x, ii) && (x = stepdown(x)) != null
                    || readCharClass(x, ii) && (x = x.next) != null
                    || readCount(x, ii)
                    || readSingleCharacter(x, ii) && (x = x.next) != null;
        }
        return true;
    }

    SRNode stepdown(SRNode x)
    {
        while (x.next != null)
        {
            x = x.next;
        }
        return x;
    }

    private boolean peekChar(int x, int[] ii)
    {
        return x == pattern.charAt(ii[0]);
    }

    private boolean readChar(int match, int[] ii, int[] ret)
    {
        if (ii[0] >= pattern.length())
        {
            return false;
        }
        int c = pattern.charAt(ii[0]);
        if (match != -1 && match != c)
        {
            return false;
        }
        if (')' == c && --ii[1] < 0)
        {
            throwEx("unmatched )", ii);
        }
        ii[0]++;
        ret[0] = c;
        return true;
    }

    private boolean readSingleCharacter(SRNode x, int[] ii)
    {
        int[] res = new int[]
        {
            -1
        };
        final int offset = ii[0];
        if (!readSpecial(ii, res, CTX1))
        {
            readChar(-1, ii, res);
        }
        CharClass cc = new CharClass();
        cc.x = (char) res[0];
        x.setNext(new SRNode(cc, x, offset));
        return true;
    }

    private boolean readGroup(SRNode x, int[] ii)
    {
        if (!readChar('(', ii, NOOP))
        {
            return false;
        }
        final int offset = ii[0];
        ii[1]++;
        if (x.GROUP || x.x != null)
        {
            SRNode tmp = new SRNode(null, x, offset);
            if (x.next != null)
            {
                x.addAny(x.next);
            }
            x.next = tmp;
            x = tmp;
        }
        x.GROUP = true;
        int start = ii[0];
        readPipe(x, ii);
        x.addAny(x.next);
        x.next = null;
        if (!readChar(')', ii, NOOP))
        {
            throwEx("unterminated group ", new int[]
            {
                start
            });
        }
        return true;
    }

    private boolean readCharClass(SRNode x, int[] ii)
    {
        if (!readChar('[', ii, NOOP))
        {
            return false;
        }
        final int offset = ii[0];
        boolean negation = false;
        int a = -1;
        CharClass cc = new CharClass();
        for (;;)
        {
            int b = nextChar(ii, CTX_CC);
            if (']' == b)
            {
                break;
            }
            else if (NEGATION == b)
            {
                negation = !negation;
                continue;
            }
            else if (CHAR_RANGE == b)
            {
                (negation ? cc.outr : cc.inr).add(new CharRange(a, nextChar(ii, CTX_CC)));
            }
            else
            {
                (negation ? cc.out : cc.in()).set(b);
            }
            if (pattern.length() == ii[0])
            {
                throwEx("unterminated character class ", new int[]
                {
                    offset
                });
            }
            a = b;
        }
        cc.simplify();
        cc = charClassCache.storeOrGet(cc);
        x.setNext(new SRNode(cc, x, offset));
        return true;
    }

    private boolean readCount(SRNode x, int[] ii)
    {
        int before = ii[0];
        if (readChar('?', ii, NOOP))
        {
            return setCount(x, 0, 1, ii);
        }
        if (readChar('*', ii, NOOP))
        {
            return setCount(x, 0, Integer.MAX_VALUE, ii);
        }
        if (readChar('+', ii, NOOP))
        {
            return setCount(x, 1, Integer.MAX_VALUE, ii);
        }
        if (readChar('{', ii, NOOP))
        {
            int min = 0, max = Integer.MAX_VALUE;
            int start = ii[0];
            while (Character.isDigit(pattern.charAt(ii[0])))
            {
                ii[0]++;
            }
            if (ii[0] > start)
            {
                min = Integer.parseInt(pattern.substring(start, ii[0]));
            }
            if (readChar(',', ii, NOOP))
            {
                start = ii[0];
                while (Character.isDigit(pattern.charAt(ii[0])))
                {
                    ii[0]++;
                }
                if (ii[0] > start)
                {
                    max = Integer.parseInt(pattern.substring(start, ii[0]));
                }
            }
            else
            {
                max = min;
            }
            if (!readChar('}', ii, NOOP))
            {
                throwEx("  } is missing", new int[]
                {
                    before
                });
            }
            setCount(x, min, max, ii);
            return true;
        }
        return false;
    }
    static private final int[] NOOP = new int[]
    {
        0
    };

    boolean setCount(SRNode x, int min, int max, int[] ii)
    {
        x.min = min;
        x.max = max;
        return true;
    }
    static final int CHAR_RANGE = -2, NEGATION = -3;

    private int nextChar(int[] ii, BitSet ctx)
    {
        int[] cc = new int[]
        {
            -1
        };
        boolean ok
                = CTX_CC == ctx && (readCharThenSet('-', ii, cc, CHAR_RANGE)
                || readCharThenSet('^', ii, cc, NEGATION))
                || readSpecial(ii, cc, ctx)
                || readChar(-1, ii, cc);
        return cc[0];
    }

    private boolean set(int[] cc, int x)
    {
        cc[0] = x;
        return true;
    }

    private boolean readCharThenSet(int match, int[] ii, int[] ret, int set)
    {
        if (!readChar(match, ii, ret))
        {
            return false;
        }
        ret[0] = set;
        return true;
    }

    private boolean readSpecial(int[] ii, int[] ret, BitSet ctx)
    {
        char c = pattern.charAt(ii[0]);
        if ('\\' != c)
        {
            return false;
        }
        if (++ii[0] == pattern.length())
        {
            throw new RuntimeException("Unterminated \\ char at end of SRegexp " + pattern);
        }
        return readUTF(ii, ret)
                || readCheck(pattern.charAt(ii[0]), ii, ret, ctx);
    }

    private boolean readUTF(int[] ii, int[] ret)
    {
        if (!readChar('u', ii, ret))
        {
            return false;
        }
        ret[0] = (char) Integer.parseInt(readn(4, ii), 16);
        return true;
    }

    private String readn(int n, int[] ii)
    {
        return pattern.substring(ii[0], ii[0] += n);
    }
    static private final BitSet CTX1 = new BitSet(), CTX_CC = new BitSet();

    static
    {
        for (char x : new char[]
        {
            '\t', '\r', '\n', '\\', '[', ']', '(', ')', '^', '$'
        })
        {
            CTX1.set(x);
        }
        for (char x : new char[]
        {
            '\t', '\r', '\n', '\\', '[', ']', '(', ')', '^', '$', '-'
        })
        {
            CTX_CC.set(x);
        }
    }

    private boolean readCheck(char ch, int[] ii, int[] ret, BitSet check)
    {
        if (!check.get(ch))
        {
            return false;
        }
        ret[0] = ch;
        ii[0]++;
        return true;
    }

    private void preprocess()
    {
        preprocessPossiblyEnding(root);
    }

    private boolean preprocessPossiblyEnding(SRNode a)
    {
        if (a == null)
        {
            return true;
        }
        a.POSSIBLY_ENDING_GROUP = preprocessPossiblyEnding(a.next);
        if (a.any != null)
        {
            for (SRNode b : a.any)
            {
                a.POSSIBLY_ENDING_GROUP &= preprocessPossiblyEnding(b);
            }
        }
        if (!a.GROUP && a.min > 0)
        {
            return false;
        }  // return false for sake of previous
        return a.POSSIBLY_ENDING_GROUP;
    }
}

class SRNode {

    CharClass x;
    SRNode prev;
    SRNode next;
    boolean GROUP = false;
    boolean POSSIBLY_ENDING_GROUP = false;
    SRNode grouppar;
    List<SRNode> any;
    int min = 1, max = 1;
    int offset = 0;
    int nestingLevel = 0;

    SRNode(CharClass x, SRNode prev, int offset)
    {
        this.x = x;
        this.prev = prev;
        this.offset = offset;
        SRNode it = prev;
        while (it != null && !it.GROUP)
        {
            it = it.prev;
        }
        if (it != null && it.GROUP)
        {
            grouppar = it;
        }
        it = prev;
        for (; it != null; nestingLevel++)
        {
            it = it.prev;
        }
    }

    SRNode setNext(SRNode c)
    {
        if (next != null)
        {
            if (GROUP)
            {
                addAny(next);
            }
            else
            {
                throw new RuntimeException("Attempt to redefine node.next");
            }
        }
        next = c;
        return this;
    }

    void addAny(SRNode c)
    {
        if (any == null)
        {
            any = new ArrayList<>();
        }
        any.add(c);
    }

    public String toString()
    {
        return (x != null ? x : "") + (GROUP ? " G {" : " {") + min + "," + max + "}";
    }

    static void print(PrintStream out, SRNode n)
    {
        print_(out, n, 0);
    }

    static void print_(PrintStream out, SRNode n, int ind)
    {
        out.print(util.Arf.repeat(' ', ind));
        out.println(n.toString());
        if (n.any != null)
        {
            //out.println(new String(f.Arf.repeat(' ', ind)) + "ANY:");
            for (SRNode c : n.any)
            {
                print_(out, c, ind + 2);
            }
            out.println(util.Arf.repeat('-', ind + 4));
        }
        if (n.next != null)
        {
            print_(out, n.next, ind + 2);
        }
    }

    SRNode ancestorByRootDistance(int distFromRoot)
    {
        if (distFromRoot > nestingLevel)
        {
            return null;
        }
        int i = nestingLevel;
        SRNode it = this;
        while (i-- > distFromRoot)
        {
            it = it.prev;
        }
        return it;
    }
}

class CharRange {

    int start, end;

    CharRange(int a, int b)
    {
        this.start = a;
        this.end = b;
    }

    boolean in(int x)
    {
        return start <= x && x <= end;
    }

    public String toString()
    {
        return (char) start + "-" + (char) end;
    }

    @Override
    public int hashCode()
    {
        return end * 31 + start;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof CharRange))
        {
            return false;
        }
        CharRange x = (CharRange) obj;
        return start == x.start && end == x.end;
    }

}

class CharClass {

    int x = -1;
    BitSet out = new BitSet(2048);
    List<CharRange> outr = new ArrayList<>();
    BitSet in = null;
    List<CharRange> inr = new ArrayList<>();

    BitSet in()
    {
        if (in == null)
        {
            in = new BitSet(2048);
        }
        return in;
    }

    void simplify()
    {
        if (in != null)
        {
            for (int j = inr.size() - 1; j >= 0; j--)
            {
                CharRange r = inr.get(j);
                int high = Math.min(r.end, in.size());
                for (int i = r.start; i <= high; i++)
                {
                    in.set(i);
                }
                if (r.end < in.size())
                {
                    inr.remove(j);
                }
            }
            for (int j = outr.size() - 1; j >= 0; j--)
            {
                CharRange r = outr.get(j);
                int high = Math.min(r.end, in.size());
                for (int i = r.start; i <= high; i++)
                {
                    in.clear(i);
                }
                if (r.end < in.size())
                {
                    outr.remove(j);
                }
            }
            for (int i = 0; i < out.length(); i++)
            {
                if (out.get(i))
                {
                    in.clear(i);
                }
            }
            out = null;
        }
        if (inr.size() == 0)
        {
            inr = null;
        }
        if (outr.size() == 0)
        {
            outr = null;
        }
    }

    boolean in(int x)
    {
        return this.x == x
                || this.x == -1
                && !listfind(outr, x)
                && (out == null || !out.get(x))
                && ((in != null && in.get(x) || in == null && inr == null || listfind(inr, x)));
    }

    private boolean listfind(List<CharRange> L, int x)
    {
        if (L == null)
        {
            return false;
        }
        for (CharRange r : L)
        {
            if (r.in(x))
            {
                return true;
            }
        }
        return false;
    }

    public String toString()
    {
        return (x != -1 ? "'" + (char) x + "'" : "[  ")
                + "  " + (outr != null ? "outr:" + outr : "")
                + "  " + (out != null ? "out:" + out : "")
                + "  " + (inr != null ? "inr:" + inr : "")
                + "  " + (in != null ? "in" + in : "");
    }

    @Override
    public int hashCode()
    {
        if (x >= 0)
        {
            return x;
        }
        int ret = RT.nullSafeHashCode(outr) * 31
                + RT.nullSafeHashCode(out) * 31
                + RT.nullSafeHashCode(in) * 31
                + RT.nullSafeHashCode(inr)
                ;
        return ret;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof CharClass))
        {
            return false;
        }
        CharClass c = (CharClass) obj;
        if (x >= 0 || c.x >= 0)
        {
            return x == c.x;
        }
        return !(!RT.nullSafeEquals(outr, c.outr)
                || !RT.nullSafeEquals(out, c.out)
                || !RT.nullSafeEquals(in, c.in)
                || !RT.nullSafeEquals(inr, c.inr));
    }

}

class Matcher {

    SRNode node;
    SRNode next;
    Matcher prev;
    boolean ok = true;
    int matchedCount = 0,
            y = -1;

    Matcher(SRNode x)
    {
        this.node = x;
    }

    int incMatchedCount(int y)
    {
        if (this.y != y)
        {
            matchedCount++;
            this.y = y;
        }
        return matchedCount;
    }

    Matcher prev_(Matcher prev)
    {
        this.prev = prev;
        return this;
    }
}
