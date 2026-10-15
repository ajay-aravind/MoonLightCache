package in.ajay;

import in.ajay.logging.ILogger;
import in.ajay.logging.SysOutLogger;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class MoonLightCache {

    private StorageEngine storageEngine;
    private ILogger logger;
    private final int LOCK_COUNT = 8000;
    private final Object[] locks = new Object[LOCK_COUNT];

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
    }

    public CommandResponse ProcessCommand(Command command) {

        //todo: validate command
        CommandResponse response= new CommandResponse();

        synchronized (this.getMutexKey(command)) {
            logger.LogDebug("Operation started");
            logger.LogDebug(command.toString());

            int active = activeThreads.incrementAndGet();
            maxActiveThreads.accumulateAndGet(active, Math::max);

            if (command.getOperation() == Operation.SET) {
                CacheItem item = new CacheItem(command.getKey(), this.getByteArrayFromValue(command.getValue(), command.getType()), command.getTtl(), command.getType());
                boolean opStatus = storageEngine.putItem(item);
                OperationResponseCode code = opStatus ? OperationResponseCode.CREATED : OperationResponseCode.FAILED;
                response.setStatusCode(code);
                response.setKey(item.getKey());
                response.setTtl(item.getTtl());
                response.setValue(this.getValueFromByteArray(item.getValue(), item.getType()));
            } else if (command.getOperation() == Operation.GET) {
                CacheItem item = storageEngine.getItem(command.getKey());
                OperationResponseCode code;
                if (item == null) {
                    code = OperationResponseCode.NOTEXISTS;
                    response.setStatusCode(code);
                } else {
                    code = OperationResponseCode.FOUND;
                    response.setStatusCode(code);
                    response.setKey(item.getKey());
                    response.setTtl(item.getTtl());
                    response.setValue(this.getValueFromByteArray(item.getValue(), item.getType()));
                }
            } else if (command.getOperation() == Operation.DELE) {
                boolean opStatus = storageEngine.DeleteIem(command.getKey());
                OperationResponseCode code = opStatus ? OperationResponseCode.DELETED : OperationResponseCode.FAILED;
                response.setStatusCode(code);
            } else if (command.getOperation() == Operation.EXISTS) {
                boolean opStatus = storageEngine.ExistsItem(command.getKey());
                OperationResponseCode code = opStatus ? OperationResponseCode.EXISTS : OperationResponseCode.NOTEXISTS;
                response.setStatusCode(code);
            } else {
                OperationResponseCode code = OperationResponseCode.INVALID_OPERATION;
                response.setStatusCode(code);
            }
            logger.LogDebug("Operation ended");
            activeThreads.decrementAndGet();
            return response;
        }
    }

    public synchronized Object getMutexKey(Command command){
        int lockIndex = Math.floorMod(command.getKey().hashCode(), LOCK_COUNT);
        return this.locks[lockIndex];
    }

    public AtomicInteger getMaxActiveThreads(){
        return this.maxActiveThreads;
    }

    private byte[] getByteArrayFromValue(String value, DataType type) {
        byte[] valueArray;

        if (type == DataType.boolType) {
            valueArray = ByteBuffer
                    .allocate(1)
                    .put((byte) (Boolean.parseBoolean(value) ? 1 : 0))
                    .array();

        } else if (type == DataType.characterType) {
            valueArray = ByteBuffer
                    .allocate(2)
                    .putChar(value.charAt(0))
                    .array();

        } else if (type == DataType.integerType) {
            valueArray = ByteBuffer
                    .allocate(4)
                    .putInt(Integer.parseInt(value))
                    .array();

        } else if (type == DataType.longType) {
            valueArray = ByteBuffer
                    .allocate(8)
                    .putLong(Long.parseLong(value))
                    .array();

        } else if (type == DataType.stringType) {
            valueArray = value.getBytes(StandardCharsets.UTF_8);

        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type);
        }

        return valueArray;
    }

    private String getValueFromByteArray(byte[] valueArray, DataType type) {

        return switch (type) {

            case boolType ->
                    String.valueOf(valueArray[0] != 0);

            case characterType ->
                    String.valueOf(ByteBuffer.wrap(valueArray).getChar());

            case integerType ->
                    String.valueOf(ByteBuffer.wrap(valueArray).getInt());

            case longType ->
                    String.valueOf(ByteBuffer.wrap(valueArray).getLong());

            case stringType ->
                    new String(valueArray, StandardCharsets.UTF_8);
        };
    }
}
