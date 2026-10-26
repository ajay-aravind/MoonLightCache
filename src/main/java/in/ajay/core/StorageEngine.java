package in.ajay.core;

import java.util.Iterator;

public interface StorageEngine {

    public boolean putItem(CacheItem item);
    public CacheItem getItem(String key);
    public boolean DeleteItem(String key);
    public boolean ExistsItem(String key);
    public Iterator<String> EnumerateItems();
    void DeleteItemIfStale(String key);
}
