package in.ajay.test;

import in.ajay.*;
import org.junit.jupiter.api.Test;

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
        setCommand.setValue("testValue");
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

    /*
    starts hundred threads setting the same value parallelly, and measure the max active current threads inside the process command synchronized section.
    Max expected value is 1, since we are issuing commands for the same key
     */
    @Test
    void TestOneThreadAtAtimeForOneKey() throws InterruptedException {
        MoonLightCache cache = new MoonLightCache();

        int threadCount = 100;
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int value = i;

            threads[i] = new Thread(() -> {
                Command setCommand = new Command();
                setCommand.setOperation(Operation.SET);
                setCommand.setValue("testValue");
                setCommand.setTtl(Instant.now().plusSeconds(10).toEpochMilli());
                setCommand.setType(DataType.stringType);
                setCommand.setKey("test");
                cache.ProcessCommand(setCommand);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        Command existsCommand = new Command(Operation.GET, "test");
        var response = cache.ProcessCommand(existsCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.FOUND);
        assertEquals(cache.getMaxActiveThreads().get(), 1);

        assertEquals(response.getValue(), "testValue");
        System.out.println("Final value: " + response.getValue());
    }

    /*
    To make sure 2 threads will be able to go into the critical section
     */
    @Test
    void TestDifferentKeysProduceDifferentLockObjects(){
        String[] keyList = {"apple", "orange", "tomato", "red", "white", "yellow", "ginger", "apple"};
        HashSet<Object> lockList = new HashSet<>();
        MoonLightCache cache = new MoonLightCache();

        for(int i=0;i<keyList.length;i++){
            lockList.add(cache.getMutexKey(new Command(Operation.GET, keyList[i])));
        }

        assertEquals(lockList.size(), 7);
    }

    /*
    To make sure more than 1 thread will enter critical section when keys are different
     */

    @Test
    void TestMoreThanOneThreadEntersCriticalSection() throws InterruptedException {
        Hashtable<String, String> keyValueMap = new Hashtable<String, String>();
        keyValueMap.put("apple", "20");
        keyValueMap.put("orange", "25");
        keyValueMap.put("tomato", "60");
        keyValueMap.put("red", "15");
        keyValueMap.put("white", "16");
        keyValueMap.put("yellow", "17");
        keyValueMap.put("ginger", "18");

        MoonLightCache cache = new MoonLightCache();
        Thread[] threadsList = new Thread[7];
        int count = 0;

        for(var key : keyValueMap.keySet()){
            Thread thread = new Thread(()->{
                Command command = new Command(Operation.SET, key, keyValueMap.get(key), Instant.now().toEpochMilli(), DataType.stringType);
                cache.ProcessCommand(command);
            });

            thread.start();
            threadsList[count++] = thread;
        }

        for(int i=0;i<count;i++){
            threadsList[i].join();
        }

        assertTrue(cache.getMaxActiveThreads().get()>1, "Expected minimum 2 threads to enter critical section");

    }

    @Test
    void TestConcurrentSetsOnASingleKey() throws InterruptedException {

        MoonLightCache cache = new MoonLightCache();

        Thread[] threadList = new Thread[1000];
        Hashtable<String, Long> valueTtlMap = new Hashtable<String, Long>();
        List<CommandResponse> responseList = Collections.synchronizedList(new ArrayList<CommandResponse>());
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();


        for(int i=0;i<1000;i++){
            valueTtlMap.put("testValue"+i, Instant.now().plusSeconds(100).toEpochMilli());
            String value = "testValue"+i;

            Thread thread = new Thread(()->{
                try {
                    startGate.await(); // Thread execution hangs here until the startGate value reaches to zero
                    Command setCommand = new Command(Operation.SET, "test", value, valueTtlMap.get(value), DataType.stringType);
                    var response = cache.ProcessCommand(setCommand);
                    responseList.add(response);
                }catch(Exception e){
                    failure.compareAndSet(null, e);
                }
            });
            threadList[i] = thread;
            thread.start();
        }

        IO.println("Threads wait till here to start..");
        // all threads start from startGatewa.await();
        startGate.countDown();
        for(var thread: threadList){
            thread.join();
        }

        assertEquals(null, failure.get());

        Command getCommand = new Command(Operation.GET, "test");
        CommandResponse response = cache.ProcessCommand(getCommand);

        String value = response.getValue();
        assertTrue(valueTtlMap.containsKey(value));
        assertEquals(valueTtlMap.get(value), response.getTtl());
        IO.println(response.toString());

        assertEquals(responseList.size(), 1000);
        for(CommandResponse cResponse: responseList){
            assertEquals(OperationResponseCode.CREATED, cResponse.getStatusCode());
        }
    }

    @Test
    void TestConcurrentSetAndDeleteOnSingleKey() throws InterruptedException{

    }

    @Test
    void TestConcurrentSetAndGetOnSingleKey() throws InterruptedException{
            MoonLightCache cache = new MoonLightCache();

            int writerCount = 4;
            int readerCount = 4;
            int writesPerThread = 5_000;
            int readsPerThread = 5_000;

            String key = "test";

            CountDownLatch startGate = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            Map<String, Long> validValues = new ConcurrentHashMap<>();

            long baseTtl = Instant.now().plusSeconds(3600).toEpochMilli();

            // Prepare all possible valid value/TTL pairs.
            for (int writer = 0; writer < writerCount; writer++) {
                for (int i = 0; i < writesPerThread; i++) {
                    String value = "writer-" + writer + "-" + i;
                    long ttl = baseTtl + writer * writesPerThread + i;
                    validValues.put(value, ttl);
                }
            }

            // Initialize the key before readers start.
            String initialValue = "initial";
            long initialTtl = baseTtl - 1;
            validValues.put(initialValue, initialTtl);

            cache.ProcessCommand(new Command(
                    Operation.SET, key, initialValue,
                    initialTtl, DataType.stringType
            ));

            Thread[] threads = new Thread[writerCount + readerCount];

            // Writer threads.
            for (int w = 0; w < writerCount; w++) {
                final int writerId = w;

                threads[w] = new Thread(() -> {
                    try {
                        startGate.await();

                        for (int i = 0; i < writesPerThread; i++) {
                            String value = "writer-" + writerId + "-" + i;
                            long ttl = baseTtl
                                    + writerId * writesPerThread + i;

                            Command command = new Command(
                                    Operation.SET, key, value,
                                    ttl, DataType.stringType
                            );

                            CommandResponse response =
                                    cache.ProcessCommand(command);

                            if (response.getStatusCode()
                                    != OperationResponseCode.CREATED) {
                                throw new AssertionError("SET failed: " + value);
                            }
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
            }

            // Reader threads.
            for (int r = 0; r < readerCount; r++) {
                threads[writerCount + r] = new Thread(() -> {
                    try {
                        startGate.await();

                        for (int i = 0; i < readsPerThread; i++) {
                            CommandResponse response = cache.ProcessCommand(
                                    new Command(Operation.GET, key)
                            );

                            if (response.getStatusCode()
                                    != OperationResponseCode.FOUND) {
                                throw new AssertionError("GET did not find key");
                            }

                            String value = response.getValue();
                            assertTrue(validValues.containsKey(value));
                            Long expectedTtl = validValues.get(value);

                            if (expectedTtl == null) {
                                throw new AssertionError(
                                        "Unexpected value: " + value);
                            }

                            if (expectedTtl.longValue() != response.getTtl()) {
                                throw new AssertionError(
                                        "Value/TTL mismatch for " + value
                                );
                            }
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    }
                });
            }

            // Start all threads.
            for (Thread thread : threads) {
                thread.start();
            }

            startGate.countDown();

            // Wait for all threads to finish.
            for (Thread thread : threads) {
                thread.join();
            }

            assertNull(failure.get(), "Concurrent GET/SET test failed");
    }
}
