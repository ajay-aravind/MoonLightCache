package in.ajay.test;

import in.ajay.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandTest {

    @Test
    void TestCreateItem() {
        Command setCommand = new Command(Operation.SET, "test", "testValue", Instant.now().plusSeconds(10).toEpochMilli(), DataType.stringType);
        MoonLightCache cache = new MoonLightCache();
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);
        assertEquals(response.getKey(), setCommand.getKey());
        assertEquals(response.getValue(), setCommand.getValue());
        assertEquals(response.getTtl(), setCommand.getTtl());
    }

    @Test
    void TestCreateIntItem(){
        Command setCommand = new Command(Operation.SET, "test", "120", Instant.now().plusSeconds(10).toEpochMilli(), DataType.integerType);
        this.TestCreateItems(setCommand);

        setCommand = new Command(Operation.SET, "test", "-1220", Instant.now().plusSeconds(10).toEpochMilli(), DataType.integerType);
        this.TestCreateItems(setCommand);
    }

    @Test
    void TestCreateLongItem(){
        Command setCommand = new Command(Operation.SET, "test", "2147483900", Instant.now().plusSeconds(10).toEpochMilli(), DataType.longType);
        this.TestCreateItems(setCommand);

        setCommand = new Command(Operation.SET, "test", "-33147483900", Instant.now().plusSeconds(10).toEpochMilli(), DataType.longType);
        this.TestCreateItems(setCommand);
    }

    @Test
    void TestCreateCharItem(){
        Command setCommand = new Command(Operation.SET, "test", "A", Instant.now().plusSeconds(10).toEpochMilli(), DataType.characterType);
        this.TestCreateItems(setCommand);
    }

    @Test
    void TestCreateBooleanItem(){
        Command setCommand = new Command(Operation.SET, "test", "false", Instant.now().plusSeconds(10).toEpochMilli(), DataType.boolType);
        this.TestCreateItems(setCommand);

        setCommand = new Command(Operation.SET, "test", "true", Instant.now().plusSeconds(10).toEpochMilli(), DataType.boolType);
        this.TestCreateItems(setCommand);
    }


    @Test
    void TestGetNonExistantItem(){
        Command getCommand = new Command(Operation.GET, "test");
        MoonLightCache cache = new MoonLightCache();
        var response = cache.ProcessCommand(getCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.NOTEXISTS);
    }

    @Test
    void TestDeleteItem(){
        Command setCommand = new Command(Operation.SET, "test", "testValue", Instant.now().plusSeconds(10).toEpochMilli(), DataType.stringType);
        MoonLightCache cache = new MoonLightCache();
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);

        Command deleteCommand = new Command(Operation.DELE, "test");
        response = cache.ProcessCommand(deleteCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.DELETED);

        Command getCommand = new Command(Operation.GET, "test");
        response = cache.ProcessCommand(getCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.NOTEXISTS);
    }

    @Test
    void TestDeleteOnNonExistantItem(){
        Command deleteCommand = new Command(Operation.DELE, "test");
        MoonLightCache cache = new MoonLightCache();
        var response = cache.ProcessCommand(deleteCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.DELETED);
    }

    @Test
    void TestExistsItem(){
        Command setCommand = new Command(Operation.SET, "test", "testValue", Instant.now().plusSeconds(10).toEpochMilli(), DataType.stringType);
        MoonLightCache cache = new MoonLightCache();
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);

        Command existsCommand = new Command(Operation.EXISTS, "test");
        response = cache.ProcessCommand(existsCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.EXISTS);
    }

    @Test
    void TestExistsItemNegative(){
        Command existsCommand = new Command(Operation.EXISTS, "nonExisitantKey");
        MoonLightCache cache = new MoonLightCache();
        CommandResponse response = cache.ProcessCommand(existsCommand);
        response = cache.ProcessCommand(existsCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.NOTEXISTS);
    }

    @Test
    void TestExpiredKeysAreDeleted(){
        Command setCommand = new Command(Operation.SET, "test", "testValue", Instant.now().plusSeconds(4).toEpochMilli(), DataType.stringType);
        MoonLightCache cache = new MoonLightCache();
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);

        Command existsCommand = new Command(Operation.EXISTS, "test");
        response = cache.ProcessCommand(existsCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.EXISTS);

        try {
            Thread.sleep(5*1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        existsCommand = new Command(Operation.EXISTS, "test");
        response = cache.ProcessCommand(existsCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.NOTEXISTS);
    }


    private void TestCreateItems(Command setCommand){
        MoonLightCache cache = new MoonLightCache();
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);
        assertEquals(response.getKey(), setCommand.getKey());
        assertEquals(response.getValue(), setCommand.getValue());
        assertEquals(response.getTtl(), setCommand.getTtl());
    }
}
