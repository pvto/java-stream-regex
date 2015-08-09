package io.streamregex;

import static util.RT.fin;
import io.FeatureInputStream;
import java.io.IOException;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RegexMapperTest {
    
    @Test public void testMapSimple() throws IOException
    {
        RegexMapper<Integer> rl = new RegexMapper.Builder()
                .mapAll(
                        "abc", 1,
                        "def", 2,
                        "aaa", 3
                )
                .build();
        FeatureInputStream in = fin("defabcaaa");
        assertEquals(2, (int)rl.readNext(in).u);
        assertEquals(1, (int)rl.readNext(in).u);
        assertEquals(3, (int)rl.readNext(in).u);
    }
    
    @Test public void testMapWsp() throws IOException
    {
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
    
    private <T> void assertReadSequence(RegexMapper<T> rl, FeatureInputStream in, T ... ts) throws IOException
    {
        for(T t : ts)
            assertEquals(t, (T)rl.readNext(in).u);
    }

    
}
