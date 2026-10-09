package in.ajay;

import java.util.Iterator;
import java.util.ListIterator;

public interface StorageEngine {

    public boolean putItem(CacheItem item);
    public CacheItem getItem(String key);
    public boolean DeleteIem(String key);
    public boolean ExistsItem(String key);
    public Iterator<String> EnumerateItems();
}
