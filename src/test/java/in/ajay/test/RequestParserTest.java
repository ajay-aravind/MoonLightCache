package in.ajay.test;

import in.ajay.core.Command;
import in.ajay.core.DataType;
import in.ajay.core.Operation;
import in.ajay.tarangam.RequestParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class RequestParserTest {

    private static final int LENGTH_PREFIX_BYTES = 4;

    // ---------- Valid SET encode/decode ----------

    @ParameterizedTest
    @EnumSource(DataType.class)
    void set_shouldRoundTripEveryDataType(DataType type)
            throws IOException {

        byte[] value = validValue(type);

        Command original = new Command(
                Operation.SET,
                "user:123",
                value,
                60_000L,
                type
        );

        byte[] encoded = RequestParser.encode(original);
        Command decoded = RequestParser.decode(encoded);

        assertEquals(Operation.SET, decoded.getOperation());
        assertEquals("user:123", decoded.getKey());
        assertEquals(type, decoded.getType());
        assertArrayEquals(value, decoded.getValue());
        assertEquals(60_000L, decoded.getTtl());
    }

    @Test
    void set_shouldEncodeCorrectFrameLength() throws IOException {
        byte[] value = "hello".getBytes(StandardCharsets.UTF_8);

        Command command = new Command(
                Operation.SET, "key", value, 1000L, DataType.stringType
        );

        byte[] encoded = RequestParser.encode(command);

        int declaredLength = ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();

        assertEquals(encoded.length - LENGTH_PREFIX_BYTES, declaredLength);
    }

    @Test
    void set_shouldSupportUnicodeKeysAndValues() throws IOException {
        String key = "用户-123";
        byte[] value = "こんにちは 🌍".getBytes(StandardCharsets.UTF_8);

        Command original = new Command(
                Operation.SET, key, value, 500L, DataType.stringType
        );

        Command decoded = RequestParser.decode(
                RequestParser.encode(original));

        assertEquals(key, decoded.getKey());
        assertArrayEquals(value, decoded.getValue());
    }

    @Test
    void set_shouldSupportEmptyStringValue() throws IOException {
        Command original = new Command(
                Operation.SET,
                "empty",
                new byte[0],
                100L,
                DataType.stringType
        );

        Command decoded = RequestParser.decode(
                RequestParser.encode(original));

        assertArrayEquals(new byte[0], decoded.getValue());
    }

    // ---------- Valid non-SET operations ----------

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {
            "GET", "EXISTS", "DELETE"
    })
    void keyOnlyOperation_shouldRoundTrip(Operation operation)
            throws IOException {

        Command original = new Command(
                operation, "user:123", null, 0L, null
        );

        byte[] encoded = RequestParser.encode(original);
        Command decoded = RequestParser.decode(encoded);

        assertEquals(operation, decoded.getOperation());
        assertEquals("user:123", decoded.getKey());
        assertNull(decoded.getValue());
        assertNull(decoded.getType());

        // TTL is not transmitted for key-only operations.
        assertEquals(0L, decoded.getTtl());
    }

    @ParameterizedTest
    @EnumSource(value = Operation.class, names = {
            "GET", "EXISTS", "DELETE"
    })
    void keyOnlyOperation_shouldNotContainValueFields(Operation operation)
            throws IOException {

        Command command = new Command(
                operation, "key", null, 0L, null
        );

        byte[] encoded = RequestParser.encode(command);

        // Frame + operation + key-length + key.
        int expectedLength =
                LENGTH_PREFIX_BYTES + 1 + Short.BYTES
                        + "key".getBytes(StandardCharsets.UTF_8).length;

        assertEquals(expectedLength, encoded.length);
    }

    // ---------- Invalid encode inputs ----------

    @Test
    void encode_shouldRejectNullCommand() {
        assertThrows(
                NullPointerException.class,
                () -> RequestParser.encode(null)
        );
    }

    @Test
    void encode_shouldRejectKeyLongerThanProtocolLimit() {
        String oversizedKey = "k".repeat(65_536);

        assertTrue(oversizedKey.getBytes(StandardCharsets.UTF_8).length > 65535);
        Command command = new Command(
                Operation.GET, oversizedKey, null, 0L, null
        );

        assertThrows(
                IOException.class,
                () -> RequestParser.encode(command)
        );
    }

    @Test
    void encode_shouldRejectValueLargerThanProtocolLimit() {
        byte[] oversizedValue = new byte[1024 * 1024 + 1];

        Command command = new Command(
                Operation.SET,
                "key",
                oversizedValue,
                100L,
                DataType.stringType
        );

        assertThrows(
                IOException.class,
                () -> RequestParser.encode(command)
        );
    }

    @Test
    void encode_shouldRejectSetWithoutDataType() {
        Command command = new Command(
                Operation.SET, "key", new byte[] {1}, 100L, null
        );

        assertThrows(
                Exception.class,
                () -> RequestParser.encode(command)
        );
    }

    // ---------- Invalid decode frames ----------

    @Test
    void decode_shouldRejectNullFrame() {
        assertThrows(
                NullPointerException.class,
                () -> RequestParser.decode(null)
        );
    }

    @Test
    void decode_shouldRejectIncompleteLengthPrefix() {
        assertThrows(
                IOException.class,
                () -> RequestParser.decode(new byte[] {0, 0, 0})
        );
    }

    @Test
    void decode_shouldRejectTruncatedFrame() throws IOException {
        byte[] encoded = RequestParser.encode(
                new Command(
                        Operation.GET, "key", null, 0L, null
                )
        );

        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(truncated)
        );
    }

    @Test
    void decode_shouldRejectExtraBytes() throws IOException {
        byte[] encoded = RequestParser.encode(
                new Command(
                        Operation.GET, "key", null, 0L, null
                )
        );

        byte[] extended = Arrays.copyOf(encoded, encoded.length + 1);

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(extended)
        );
    }

    @Test
    void decode_shouldRejectUnknownOperationCode() throws IOException {
        byte[] frame = rawFrame(new byte[] {
                (byte) 0xFF, // Unknown operation
                0, 1,        // Key length
                'k'
        });

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectEmptyKey() throws IOException {
        byte[] frame = rawFrame(new byte[] {
                (byte) Operation.GET.code(),
                0, 0
        });

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectUnknownDataType() throws IOException {
        byte[] frame = rawSetFrame(
                (byte) 0xFF,
                new byte[] {1},
                100L
        );

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectValueLengthLargerThanLimit()
            throws IOException {

        ByteBuffer body = ByteBuffer.allocate(
                1 + 2 + 1 + 1 + 4 + 8
        ).order(ByteOrder.BIG_ENDIAN);

        body.put((byte) Operation.SET.code());
        body.putShort((short) 1);
        body.put((byte) 'k');
        body.put((byte) DataType.stringType.code());
        body.putInt(1024 * 1024 + 1);
        body.putLong(100L);

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(rawFrame(body.array()))
        );
    }

    @Test
    void decode_shouldRejectNegativeTtlBelowMinusOne()
            throws IOException {

        byte[] frame = rawSetFrame(
                (byte) DataType.stringType.code(),
                new byte[] {'x'},
                -2L
        );

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectInvalidBooleanByte() throws IOException {
        byte[] frame = rawSetFrame(
                (byte) DataType.boolType.code(),
                new byte[] {2},
                100L
        );

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectMalformedSetValueLength()
            throws IOException {

        // The value length claims 5 bytes, but only 1 value byte exists.
        ByteBuffer body = ByteBuffer.allocate(
                1 + 2 + 1 + 1 + 4 + 1 + 8
        ).order(ByteOrder.BIG_ENDIAN);

        body.put((byte) Operation.SET.code());
        body.putShort((short) 1);
        body.put((byte) 'k');
        body.put((byte) DataType.stringType.code());
        body.putInt(5);
        body.put((byte) 'x');
        body.putLong(100L);

        assertThrows(
                IOException.class,
                () -> RequestParser.decode(rawFrame(body.array()))
        );
    }

    // ---------- Test helpers ----------

    private static byte[] validValue(DataType type) {
        return switch (type) {
            case stringType ->
                    "hello".getBytes(StandardCharsets.UTF_8);
            case integerType ->
                    ByteBuffer.allocate(Integer.BYTES)
                            .order(ByteOrder.BIG_ENDIAN)
                            .putInt(42)
                            .array();
            case longType ->
                    ByteBuffer.allocate(Long.BYTES)
                            .order(ByteOrder.BIG_ENDIAN)
                            .putLong(123456789L)
                            .array();
            case boolType -> new byte[] {1};
            case characterType ->
                    ByteBuffer.allocate(Character.BYTES)
                            .order(ByteOrder.BIG_ENDIAN)
                            .putChar('A')
                            .array();
        };
    }

    private static byte[] rawFrame(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(
                Integer.BYTES + payload.length
        ).order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    private static byte[] rawSetFrame(
            byte typeCode, byte[] value, long ttl) {

        byte[] key = {'k'};

        ByteBuffer body = ByteBuffer.allocate(
                1 + Short.BYTES + key.length
                        + 1 + Integer.BYTES + value.length + Long.BYTES
        ).order(ByteOrder.BIG_ENDIAN);

        body.put((byte) Operation.SET.code());
        body.putShort((short) key.length);
        body.put(key);
        body.put(typeCode);
        body.putInt(value.length);
        body.put(value);
        body.putLong(ttl);

        return rawFrame(body.array());
    }
}
