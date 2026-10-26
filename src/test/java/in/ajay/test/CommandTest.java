package in.ajay.test;

import in.ajay.core.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandTest {

    private MoonLightCache cache;

    @BeforeEach
    void setUp() {
        cache = new MoonLightCache();
    }

    @AfterEach
    void tearDown() {
        cache.stop();
    }

    @Test
    void TestCreateItem() {
        Command setCommand = new Command(Operation.SET, "test", "testValue".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.stringType);
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);
        assertEquals(response.getKey(), setCommand.getKey());
        assertArrayEquals(response.getValue(), setCommand.getValue());
        assertEquals(response.getType(), setCommand.getType());
        assertEquals(response.getTtl(), setCommand.getTtl());
    }

    @Test
    void TestCreateIntItem(){
        Command setCommand = new Command(Operation.SET, "test", "120".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.integerType);
        this.TestCreateItems(setCommand);

        setCommand = new Command(Operation.SET, "test", "-1220".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.integerType);
        this.TestCreateItems(setCommand);
    }

    @Test
    void TestCreateLongItem(){
        Command setCommand = new Command(Operation.SET, "test", "2147483900".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.longType);
        this.TestCreateItems(setCommand);

        setCommand = new Command(Operation.SET, "test", "-33147483900".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.longType);
        this.TestCreateItems(setCommand);
    }

    @Test
    void TestCreateCharItem(){
        Command setCommand = new Command(Operation.SET, "test", "A".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.characterType);
        this.TestCreateItems(setCommand);
    }

    @Test
    void TestCreateBooleanItem(){
        Command setCommand = new Command(Operation.SET, "test", "false".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.boolType);
        this.TestCreateItems(setCommand);

        setCommand = new Command(Operation.SET, "test", "true".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.boolType);
        this.TestCreateItems(setCommand);
    }


    @Test
    void TestGetNonExistantItem(){
        Command getCommand = new Command(Operation.GET, "test");
        var response = cache.ProcessCommand(getCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.NOTEXISTS);
    }

    @Test
    void TestDeleteItem(){
        Command setCommand = new Command(Operation.SET, "test", "testValue".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.stringType);
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);

        Command deleteCommand = new Command(Operation.DELETE, "test");
        response = cache.ProcessCommand(deleteCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.DELETED);

        Command getCommand = new Command(Operation.GET, "test");
        response = cache.ProcessCommand(getCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.NOTEXISTS);
    }

    @Test
    void TestDeleteOnNonExistantItem(){
        Command deleteCommand = new Command(Operation.DELETE, "test");
        var response = cache.ProcessCommand(deleteCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.DELETED);
    }

    @Test
    void TestExistsItem(){
        Command setCommand = new Command(Operation.SET, "test", "testValue".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(10).toEpochMilli(), DataType.stringType);
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);

        Command existsCommand = new Command(Operation.EXISTS, "test");
        response = cache.ProcessCommand(existsCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.EXISTS);
    }

    @Test
    void TestExistsItemNegative(){
        Command existsCommand = new Command(Operation.EXISTS, "nonExisitantKey");
        CommandResponse response = cache.ProcessCommand(existsCommand);
        response = cache.ProcessCommand(existsCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.NOTEXISTS);
    }

    @Test
    void TestExpiredKeysAreDeleted(){
        Command setCommand = new Command(Operation.SET, "test", "testValue".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(4).toEpochMilli(), DataType.stringType);
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

    /*
    Make background thread deletes the stale entries.
    Directly testing the key existence in data layer instead of issuing get command
     */
    @Test
    void TestBackgroundThreadDeletesStaleEntries() throws InterruptedException {
        Command setCommand = new Command(Operation.SET, "test", "testValue".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(2).toEpochMilli(), DataType.stringType);
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);

        Thread.sleep(1000*3);
        var iterator = cache.getKeysListInCache();
        Boolean keyExists = false;
        for (Iterator<String> it = iterator; it.hasNext(); ) {
            String key = it.next();
            if(key.equals("test")){
                keyExists = true;
            }
        }
        assertEquals(false, keyExists);
    }

    @Test
    void TestBackgroundThreadDoNotDeletesActiveEntries() throws InterruptedException {
        Command setCommand = new Command(Operation.SET, "test1", "testValue".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(2).toEpochMilli(), DataType.stringType);
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);

        setCommand = new Command(Operation.SET, "test2", "testValue".getBytes(StandardCharsets.UTF_8), Instant.now().plusSeconds(2).toEpochMilli(), DataType.stringType);
        response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);

        var iterator = cache.getKeysListInCache();
        int count = 0;

        for (Iterator<String> it = iterator; it.hasNext(); ) {
            String key = it.next();
            count++;
        }
        assertEquals(2, count);
    }


    private void TestCreateItems(Command setCommand){
        CommandResponse response = cache.ProcessCommand(setCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);
        assertEquals(response.getKey(), setCommand.getKey());
        assertArrayEquals(response.getValue(), setCommand.getValue());
        assertEquals(response.getTtl(), setCommand.getTtl());
        assertEquals(response.getType(), setCommand.getType());
    }
}
