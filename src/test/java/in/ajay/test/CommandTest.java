package in.ajay.test;

import in.ajay.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CommandTest {

    @Test
    void TestCreateItem() {
        Command getCommand = new Command(Operation.SET, "test", "testValue", Instant.now().plusSeconds(10).toEpochMilli(), DataType.stringType);
        MoonLightCache cache = new MoonLightCache();
        CommandResponse response = cache.ProcessRequest(getCommand);
        assertEquals(response.getStatusCode(), OperationResponseCode.CREATED);
        assertEquals(response.getKey(), getCommand.getKey());
        assertEquals(response.getValue(), getCommand.getValue());
        assertEquals(response.getTtl(), getCommand.getTtl());
    }


}
