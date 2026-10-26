package in.ajay.core;

import in.ajay.logging.ILogger;
import in.ajay.logging.SysOutLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MoonLightCache {

    private final StorageEngine storageEngine;
    private ILogger logger;
    private final int LOCK_COUNT = 8000;
    private final Object[] locks = new Object[LOCK_COUNT];

    private final int INTIAL_DELAY = AppConfig.getInt(
            "maintenance.initial.delay.seconds");

    private final int INTERVAL = AppConfig.getInt(
            "maintenance.interval.seconds");

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cache-maintenance");
                t.setDaemon(true);
                return t;
            });

    // Testing variables

    private AtomicInteger activeThreads = new AtomicInteger(0);
    private AtomicInteger maxActiveThreads = new AtomicInteger(0);

    public MoonLightCache(StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
    }

    public MoonLightCache() {
        this.storageEngine = new InMemoryStorageEngine();
        logger = new SysOutLogger();

        // initializing lock objects
        for(int i=0;i<LOCK_COUNT;i++){
            locks[i] = new Object();
        }
        // start background thread to remove stale entries
        this.startMaintananceThread();
    }

    public CommandResponse ProcessCommand(Command command) {

        //todo: validate command
        CommandResponse response= new CommandResponse();

        synchronized (this.getMutexKey(command.getKey())) {
//            logger.LogDebug("Operation started");
//            logger.LogDebug(command.toString());

            int active = activeThreads.incrementAndGet();
            maxActiveThreads.accumulateAndGet(active, Math::max);

            if (command.getOperation() == Operation.SET) {
                CacheItem item = new CacheItem(command.getKey(), command.getValue(), command.getTtl(), command.getType());
                boolean opStatus = storageEngine.putItem(item);
                OperationResponseCode code = opStatus ? OperationResponseCode.CREATED : OperationResponseCode.FAILED;
                response.setStatusCode(code);
                response.setKey(item.getKey());
                response.setTtl(item.getTtl());
                response.setValue(item.getValue());
                response.setType(item.getType());
            } else if (command.getOperation() == Operation.GET) {
                CacheItem item = storageEngine.getItem(command.getKey());
                OperationResponseCode code;
                if (item == null) {
                    code = OperationResponseCode.NOTEXISTS;
                    response.setStatusCode(code);
                    response.setKey(command.getKey());
                } else {
                    code = OperationResponseCode.FOUND;
                    response.setStatusCode(code);
                    response.setKey(item.getKey());
                    response.setTtl(item.getTtl());
                    response.setValue(item.getValue());
                    response.setType(item.getType());
                }
            } else if (command.getOperation() == Operation.DELETE) {
                boolean opStatus = storageEngine.DeleteItem(command.getKey());
                OperationResponseCode code = opStatus ? OperationResponseCode.DELETED : OperationResponseCode.FAILED;
                response.setStatusCode(code);
                response.setKey(command.getKey());
            } else if (command.getOperation() == Operation.EXISTS) {
                boolean opStatus = storageEngine.ExistsItem(command.getKey());
                OperationResponseCode code = opStatus ? OperationResponseCode.EXISTS : OperationResponseCode.NOTEXISTS;
                response.setStatusCode(code);
                response.setKey(command.getKey());
            } else {
                OperationResponseCode code = OperationResponseCode.INVALID_OPERATION;
                response.setStatusCode(code);
            }
//            logger.LogDebug("Operation ended");
            activeThreads.decrementAndGet();
            return response;
        }
    }

    public synchronized Object getMutexKey(String key){
        int lockIndex = Math.floorMod(key.hashCode(), LOCK_COUNT);
        return this.locks[lockIndex];
    }

    public void startMaintananceThread() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                this.RemoveStaleEntries(this.storageEngine.EnumerateItems());
            } catch (Exception e) {
                e.printStackTrace();
                this.logger.LogError(e.getMessage());
            }
        }, INTIAL_DELAY, INTERVAL, TimeUnit.SECONDS);
        this.logger.LogInfo("Maintenance Thread scheduled");
    }

    public void stop(){
        this.logger.LogInfo("Maintenance scheduler stopped");
        this.scheduler.shutdown();
    }

    public void RemoveStaleEntries(Iterator<String> mapIterator){
        this.logger.LogTrace("Started to remove stale cache entries");
        for (Iterator<String> it = mapIterator; it.hasNext(); ) {
            String key = it.next();
            synchronized (this.getMutexKey(key)) {
                this.logger.LogTrace("Removing stale key: " + key+" from cache");
                this.storageEngine.DeleteItemIfStale(key);
            }
        }
        this.logger.LogTrace("Done removing stale cache entries");
    }

    /*
    Debug methods
     */
    public AtomicInteger getMaxActiveThreads(){
        return this.maxActiveThreads;
    }

    public Iterator<String> getKeysListInCache(){
        return this.storageEngine.EnumerateItems();
    }
}
