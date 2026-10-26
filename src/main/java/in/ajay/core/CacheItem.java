package in.ajay.core;

import java.util.Arrays;
import java.util.Objects;

public class CacheItem {
    private String key;
    private byte[] value;

    /*
    represents the epoch till when this key is valid
     */
    private long ttl;
    private DataType type;

    public CacheItem(String key, byte[] value, long ttl, DataType type) {
        this.key = key;
        this.value = value;
        this.ttl = ttl;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CacheItem cacheItem)) return false;
        return ttl == cacheItem.ttl && Objects.equals(key, cacheItem.key) && Objects.deepEquals(value, cacheItem.value) && type == cacheItem.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, Arrays.hashCode(value), ttl, type);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public byte[] getValue() {
        return value;
    }

    public void setValue(byte[] value) {
        this.value = value;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public DataType getType() {
        return type;
    }

    public void setType(DataType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "CacheItem{" +
                "key='" + key + '\'' +
                ", value=" + Arrays.toString(value) +
                ", ttl=" + ttl +
                ", type=" + type +
                '}';
    }
}

