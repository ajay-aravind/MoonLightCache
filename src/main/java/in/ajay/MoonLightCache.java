package in.ajay;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MoonLightCache {

    StorageEngine storageEngine;

    public MoonLightCache(StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
    }

    public MoonLightCache() {
        this.storageEngine = new InMemoryStorageEngine();
    }

    public CommandResponse ProcessRequest(Command command){

        //todo: validate command

        CommandResponse response;

        if(command.getOperation() == Operation.SET){
            CacheItem item = new CacheItem(command.getKey(), this.getByteArrayFromValue(command.getValue(),command.getType()), command.getTtl(), command.getType());
            boolean opStatus = storageEngine.putItem(item);
            OperationResponseCode code = opStatus ? OperationResponseCode.CREATED : OperationResponseCode.FAILED;
            response = new CommandResponse(code, item.getKey(), command.getValue(), item.getTtl());
        }
        else if(command.getOperation() == Operation.GET){
            CacheItem item = storageEngine.getItem(command.getKey());
            OperationResponseCode code;
            if(item == null){
                code = OperationResponseCode.NOTEXISTS;
                response = new CommandResponse(code);
            }else{
                code = OperationResponseCode.FOUND;
                response = new CommandResponse(code, item.getKey(), this.getValueFromByteArray(item.getValue(), item.getType()), item.getTtl());
            }
        }
        else if(command.getOperation() == Operation.DELE){
            boolean opStatus = storageEngine.DeleteIem(command.getKey());
            OperationResponseCode code = opStatus ? OperationResponseCode.DELETED : OperationResponseCode.FAILED;
            response = new CommandResponse(code);
        }
        else if(command.getOperation() == Operation.EXISTS){
            boolean opStatus = storageEngine.ExistsItem(command.getKey());
            OperationResponseCode code = opStatus ? OperationResponseCode.EXISTS : OperationResponseCode.NOTEXISTS;
            response = new CommandResponse(code);
        }
        else{
            OperationResponseCode code = OperationResponseCode.INVALID_OPERATION;
            response = new CommandResponse(code);
        }

        return response;
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
