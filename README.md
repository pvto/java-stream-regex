#Java stream regex

This is a quick (3 working days) implementation for doing regular exceptions over streams in java.

My main interest was to parse tokens directly from streams.  This is a neat thing to do in a parser.

##Usage

## Reading from a stream

```
    InputStream in = new InputStream() {
        int i = 0;
        @Override public int read() throws IOException {
            if (i++ > 10000000) {return -1;}
            return 'a'+(i%2);
        }
    };
    FeatureInputStream fin = new FeatureInputStream(in);
    assertTrue(new StreamRegex("(ab)*").matches(fin);
```

## Mapping to tokens

```
    @Test public void testMapWsp() throws IOException {
        RegexMapper<Integer> rl = new RegexMapper.Builder()
                .mapAll(
                        "[ \t\r\n]+", 0,
                        "a+b?", 1,
                        "b+", 2
                )
                .build();
        FeatureInputStream in = fin("  ab bb aaabb");
        assertReadSequence(rl, in, 0,1,0,2,0,1,2,null);
    }
    
    private <T> void assertReadSequence(RegexMapper<T> rl, FeatureInputStream in, T ... ts) throws IOException {
        for(T t : ts)
            assertEquals(t, (T)rl.readNext(in).u);
    }
``` 

In the above test I create three regexes (whitespace), "a+b?", "b+", then map them to the integers 0, 1, 2, respectively.
I create a special input stream from a string (utility method util.RT.fin()), and then apply the mapper.
This test passes, reading the following input tokens: "  ", "ab", " ", "bb", "aaab", "b", null (signals stream end).

##Performance and regex compliance considerations

Regex compliance is limited.  Only read and match operations are supported.  Only a restricted subset of standard regex features is supported, omitting capturing groups and other more complex features.

Currently performance is 20–40 times slower than a simple if-then automaton.

Things to optimize include character class matching and conflict resolution (which is now based on a dynamic list of candidate matches).
