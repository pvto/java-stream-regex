
package util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SimpleHashCache<T> {

    private HashMap<Integer, List<T>> delegate = new HashMap<>();
    private int size = 0;
    
    public T storeOrGet(T val)
    {
        int hashCode = val.hashCode();
        List<T> old = (List<T>)delegate.get(hashCode);
        if (old != null)
        {
            for(T t : old)
                if (t.equals(val))
                    return t;
        }
        else
        {
            old = new ArrayList<>();
            delegate.put(hashCode, old);
        }
        old.add(val);
        size++;
        return val;
    }
    
    public int size() { return size; }
}
