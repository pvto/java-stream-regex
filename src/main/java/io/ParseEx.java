package io;

public class ParseEx extends RuntimeException {
    
    public ParseEx(FeatureInputStream fin, String message)
    {
        super(m(fin,message));
    }
    
    public ParseEx(FeatureInputStream fin, String message, Exception y)
    {
        super(m(fin,message), y);
    }
    
    static private String m(FeatureInputStream fin, String message)
    {
         return message+" at line "+fin.getLine()+":"+fin.getCol();
    }
}