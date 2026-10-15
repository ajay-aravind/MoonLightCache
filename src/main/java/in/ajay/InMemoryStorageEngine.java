package in.ajay;

import java.time.Instant;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentHashMap;

//Doesn't do any validations i.e null checks on data provided, validations has to be done on upper layers
//todo cleanup stale entires that built up
    // 1. Run a function at every specified interval
    // 2. when the system throw oom exception
public class InMemoryStorageEngine implements  StorageEngine{

    private final ConcurrentHashMap<String, CacheItem> items;

    public InMemoryStorageEngine() {
        this.items = new ConcurrentHashMap<String, CacheItem>(1000*1000*60);
    }

    @Override
    public boolean putItem(CacheItem item) {
        try {
            items.put(item.getKey(), item);
        }catch(Exception exception){
            System.out.println(exception.getMessage());
            return false;
        }
        return true;
    }

    /// returns null or cacheItem
    /// @param key
    /// @return
    @Override
    public synchronized CacheItem getItem(String key) {
        CacheItem item =  items.get(key);
        if(item != null && item.getTtl() < Instant.now().toEpochMilli()){
            this.DeleteIem(key);
            return null;
        }
        return item;
    }

    @Override
    public boolean DeleteIem(String key) {
         items.remove(key);
         return true;
    }

    @Override
    public boolean ExistsItem(String key) {
        return this.getItem(key)!=null;
    }

    @Override
    public Iterator<String> EnumerateItems() {
        return this.items.keySet().iterator();
    }
}
