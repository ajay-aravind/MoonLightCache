package in.ajay.test;

import in.ajay.core.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class PerfTest {

    @Test
    void TestOneMillionKey(){
        MoonLightCache cache = new MoonLightCache();

        Command setCommand = new Command();
        setCommand.setOperation(Operation.SET);
        setCommand.setValue("testValue".getBytes(StandardCharsets.UTF_8));
        setCommand.setTtl(Instant.now().plusSeconds(10).toEpochMilli());
        setCommand.setType(DataType.stringType);

        for(int i=0;i<1000*1000*50;i++){
            /*
            cache -> <Key, CacheItem>
            key: 16 bytes
            cacheItem ref: 4 bytes
            cacheItem:
                test: 4 chars, 8 bytes max, average 4 digits i.e 8 bytes, total 16 bytes
                testValue: 9chars, 18 bytes
                ttl: 8 bytes
                enum: 4 byte, object references
            total: 16 + 4 + 16 + 18 + 8 + 4  = 66 bytes
            50M * 66B = 3300MB
             */
            setCommand.setKey("test"+i);
            var response = cache.ProcessCommand(setCommand);
            if(i%(1000*1000) == 0){
                System.out.println((i/(1000*1000))+ "M");
                System.out.println(response);
            }
        }
    }


}
