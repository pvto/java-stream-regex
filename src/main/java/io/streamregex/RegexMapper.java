
package io.streamregex;

import io.FeatureInputStream;
import struct.Duple;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A StreamRegex that returns mappings, mapping an item with 
 * the string that was just accepted.
 * 
 * @author pvto https://github.com/pvto
 * @param <T> the type of objects referenced
 */
public class RegexMapper<T> extends StreamRegex {
    
    public final List<T> objectRefs;

    private RegexMapper(String regex, List<T> refs)
    { 
        super(regex);
        objectRefs = refs;
    }
    
    public Duple<String, T> readNext(FeatureInputStream in) throws IOException
    {
        String s = super.readItem(in);
        return new Duple(s, s == null ? null : getReferendFromLastRead());
    }
    
    public T getReferendFromLastRead()
    {
        System.out.println(objectRefs);
        System.out.println(super.pattern);
        return objectRefs.get(super.getLastMatchingFragmentOffset());
    }

    public static class Builder<T> {

        private final List<Duple<String,T>> mappings = new ArrayList<>();
        
        public Builder map(String s, T terminal)
        {
            mappings.add(new Duple(s, terminal));
            return this;
        }
        public Builder mapAll(Object ... pairs)
        {
            for(int i = 0; i < pairs.length; )
            {
                map((String)pairs[i++], (T)pairs[i++]);
            }
            return this;
        }
        public RegexMapper build()
        {
            StringBuilder str = new StringBuilder('(');
            List<T> refs = new ArrayList<>();
            refs.add(null); // for the encompassing group
            refs.add(null); // for root
            int i = 0;
            for(Duple<String,T> d : this.mappings)
            {
                str.append('(').append(d.t).append(')').append('|');
                refs.add(d.u);
                int j = i + 1;
                while(j < i + 3 + d.t.length())
                {
                    j++;
                    refs.add(null);
                }
                i = j;
            }
            str.setLength(str.length() - 1);
            str.append(")");
            RegexMapper msregex = new RegexMapper(str.toString(), refs);
            return msregex;
        }
    }

    

}
