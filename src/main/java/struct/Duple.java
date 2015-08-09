
package struct;

import util.RT;

public class Duple<T,U> {

    public T t;
    public U u;
    
    public Duple(T t, U u) { this.t = t; this.u = u; }

    
    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof Duple)) return false;
        Duple<T,U> b = (Duple<T,U>) obj;
        return RT.nullSafeEquals(t, b.t)
                && RT.nullSafeEquals(u, b.u);
    }

    @Override
    public int hashCode()
    {
        return RT.nullSafeHashCode(t) * 21 
                + RT.nullSafeHashCode(u);
    }
    
    
    
    @Override public String toString() { return String.format("<%s,%s>", t, u); }
}
