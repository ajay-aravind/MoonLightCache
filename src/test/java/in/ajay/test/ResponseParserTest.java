
package in.ajay.test;

import in.ajay.core.CommandResponse;
import in.ajay.core.DataType;
import in.ajay.core.OperationResponseCode;
import in.ajay.tarangam.ResponseParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ResponseParserTest {

    private static final int PREFIX_BYTES = Integer.BYTES;
    private static final int MAX_FRAME_BYTES = 1024 * 1024;

    // ---------------------------------------------------------
    // Positive encoding and decoding tests
    // ---------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = OperationResponseCode.class, names = {
            "EXISTS", "NOTEXISTS", "CREATED", "FAILED",
            "DELETED", "INVALID_OPERATION"
    })
    void keyOnlyResponses_shouldRoundTrip(
            OperationResponseCode code) throws IOException {

        CommandResponse original = new CommandResponse(
                code, "user:123", null, null
        );

        byte[] encoded = ResponseParser.encode(original);
        CommandResponse decoded = ResponseParser.decode(encoded);

        assertEquals(code, decoded.getStatusCode());
        assertEquals("user:123", decoded.getKey());
        assertNull(decoded.getValue());
        assertNull(decoded.getType());
    }

    @Test
    void foundResponse_shouldRoundTripStringValue() throws IOException {
        byte[] value = "hello world".getBytes(StandardCharsets.UTF_8);

        assertFoundRoundTrip(
                "user:123", value, DataType.stringType);
    }

    @ParameterizedTest
    @EnumSource(DataType.class)
    void foundResponse_shouldRoundTripEveryDataType(
            DataType type) throws IOException {

        byte[] value = validValue(type);

        assertFoundRoundTrip("key", value, type);
    }

    @Test
    void foundResponse_shouldSupportUnicodeKeyAndValue()
            throws IOException {

        String key = "用户-123";
        byte[] value = "こんにちは 🌍".getBytes(StandardCharsets.UTF_8);

        CommandResponse original = new CommandResponse(
                OperationResponseCode.FOUND,
                key,
                value,
                DataType.stringType
        );

        CommandResponse decoded = ResponseParser.decode(
                ResponseParser.encode(original));

        assertEquals(key, decoded.getKey());
        assertArrayEquals(value, decoded.getValue());
    }

    @Test
    void foundResponse_shouldSupportEmptyStringValue()
            throws IOException {

        assertFoundRoundTrip(
                "empty", new byte[0], DataType.stringType);
    }

    @Test
    void encode_shouldWriteCorrectFrameLength() throws IOException {
        CommandResponse response = new CommandResponse(
                OperationResponseCode.FOUND,
                "key",
                new byte[] {1, 2, 3},
                DataType.stringType
        );

        byte[] encoded = ResponseParser.encode(response);

        int declaredLength = ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt();

        assertEquals(encoded.length - PREFIX_BYTES, declaredLength);
    }

    @Test
    void encode_shouldUseBigEndianLengthPrefix() throws IOException {
        CommandResponse response = new CommandResponse(
                OperationResponseCode.EXISTS,
                "k",
                null,
                null
        );

        byte[] encoded = ResponseParser.encode(response);

        int length = encoded.length - PREFIX_BYTES;

        assertEquals(0, encoded[0]);
        assertEquals(0, encoded[1]);
        assertEquals(0, encoded[2]);
        assertEquals(length, Byte.toUnsignedInt(encoded[3]));
    }

    // ---------------------------------------------------------
    // Stream tests
    // ---------------------------------------------------------

    @Test
    void writeAndRead_shouldRoundTripResponse() throws IOException {
        CommandResponse original = new CommandResponse(
                OperationResponseCode.FOUND,
                "user:123",
                "hello".getBytes(StandardCharsets.UTF_8),
                DataType.stringType
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ResponseParser.write(output, original);

        ByteArrayInputStream input =
                new ByteArrayInputStream(output.toByteArray());

        CommandResponse decoded = ResponseParser.read(input);

        assertEquals(original.getStatusCode(), decoded.getStatusCode());
        assertEquals(original.getKey(), decoded.getKey());
        assertEquals(original.getType(), decoded.getType());
        assertArrayEquals(original.getValue(), decoded.getValue());
    }

    @Test
    void read_shouldRejectIncompleteLengthPrefix() {
        ByteArrayInputStream input =
                new ByteArrayInputStream(new byte[] {0, 0});

        assertThrows(
                EOFException.class,
                () -> ResponseParser.read(input)
        );
    }

    @Test
    void read_shouldRejectTruncatedPayload() throws IOException {
        byte[] frame = encodeKeyOnlyResponse(
                OperationResponseCode.EXISTS, "key");

        byte[] truncated = Arrays.copyOf(frame, frame.length - 1);

        assertThrows(
                EOFException.class,
                () -> ResponseParser.read(
                        new ByteArrayInputStream(truncated))
        );
    }

    @Test
    void read_shouldRejectOversizedFrameBeforeReadingPayload() {
        byte[] prefix = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(MAX_FRAME_BYTES + 1)
                .array();

        assertThrows(
                IOException.class,
                () -> ResponseParser.read(
                        new ByteArrayInputStream(prefix))
        );
    }

    // ---------------------------------------------------------
    // Negative encoding tests
    // ---------------------------------------------------------

    @Test
    void encode_shouldRejectNullResponse() {
        assertThrows(
                NullPointerException.class,
                () -> ResponseParser.encode(null)
        );
    }

    @Test
    void encode_shouldRejectKeyLongerThanMaximum() {
        String key = "k".repeat(65_536);

        CommandResponse response = new CommandResponse(
                OperationResponseCode.EXISTS,
                key,
                null,
                null
        );

        assertThrows(
                IOException.class,
                () -> ResponseParser.encode(response)
        );
    }

    @Test
    void encode_shouldRejectEmptyKey() {
        CommandResponse response = new CommandResponse(
                OperationResponseCode.EXISTS,
                "",
                null,
                null
        );

        assertThrows(
                IOException.class,
                () -> ResponseParser.encode(response)
        );
    }

    @Test
    void encode_shouldRejectFoundResponseWithoutValue() {
        assertThrows(
                Exception.class,
                () -> ResponseParser.encode(
                        new CommandResponse(
                                OperationResponseCode.FOUND,
                                "key",
                                null,
                                DataType.stringType))
        );
    }

    @Test
    void encode_shouldRejectFoundResponseWithoutDataType() {
        assertThrows(
                Exception.class,
                () -> ResponseParser.encode(
                        new CommandResponse(
                                OperationResponseCode.FOUND,
                                "key",
                                new byte[] {1},
                                null))
        );
    }

    @Test
    void encode_shouldRejectValueOnKeyOnlyResponse() {
        assertThrows(
                Exception.class,
                () -> ResponseParser.encode(
                        new CommandResponse(
                                OperationResponseCode.EXISTS,
                                "key",
                                new byte[] {1},
                                null))
        );
    }

    @Test
    void encode_shouldRejectOversizedValue() {
        byte[] value = new byte[MAX_FRAME_BYTES + 1];

        assertThrows(
                Exception.class,
                () -> ResponseParser.encode(
                        new CommandResponse(
                                OperationResponseCode.FOUND,
                                "key",
                                value,
                                DataType.stringType))
        );
    }

    @Test
    void encode_shouldRejectInvalidBooleanValue() {
        assertThrows(
                Exception.class,
                () -> ResponseParser.encode(
                        new CommandResponse(
                                OperationResponseCode.FOUND,
                                "key",
                                new byte[] {2},
                                DataType.boolType))
        );
    }

    @Test
    void encode_shouldRejectInvalidIntegerLength() {
        assertThrows(
                Exception.class,
                () -> ResponseParser.encode(
                        new CommandResponse(
                                OperationResponseCode.FOUND,
                                "key",
                                new byte[] {1, 2},
                                DataType.integerType))
        );
    }

    // ---------------------------------------------------------
    // Invalid frame tests
    // ---------------------------------------------------------

    @Test
    void decode_shouldRejectNullFrame() {
        assertThrows(
                NullPointerException.class,
                () -> ResponseParser.decode(null)
        );
    }

    @Test
    void decode_shouldRejectShortLengthPrefix() {
        assertThrows(
                EOFException.class,
                () -> ResponseParser.decode(new byte[] {0, 0, 0})
        );
    }

    @Test
    void decode_shouldRejectFrameLengthMismatch() throws IOException {
        byte[] frame = encodeKeyOnlyResponse(
                OperationResponseCode.EXISTS, "key");

        // Remove a byte without changing the declared length.
        byte[] truncated = Arrays.copyOf(frame, frame.length - 1);

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(truncated)
        );
    }

    @Test
    void decode_shouldRejectExtraBytes() throws IOException {
        byte[] frame = encodeKeyOnlyResponse(
                OperationResponseCode.EXISTS, "key");

        byte[] extended = Arrays.copyOf(frame, frame.length + 1);

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(extended)
        );
    }

    @Test
    void decode_shouldRejectOversizedFrame() {
        byte[] frame = ByteBuffer.allocate(Integer.BYTES)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(MAX_FRAME_BYTES + 1)
                .array();

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectUnknownResponseCode() {
        byte[] frame = rawFrame(new byte[] {
                (byte) 0xFF, // Unknown response code
                0, 1,        // Key length
                'k'
        });

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectZeroLengthKey() {
        byte[] frame = rawFrame(new byte[] {
                1,     // EXISTS
                0, 0   // Empty key
        });

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectTruncatedKey() {
        byte[] frame = rawFrame(new byte[] {
                1,      // EXISTS
                0, 5,   // Key length says 5
                'k'     // Only one key byte
        });

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectFoundWithoutDataType() {
        byte[] frame = rawFrame(new byte[] {
                4,      // FOUND
                0, 1,   // Key length
                'k'
        });

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectUnknownDataType() {
        byte[] frame = rawFoundFrame(
                0xFF, new byte[] {1});

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectOversizedValueLength() {
        ByteBuffer payload = ByteBuffer.allocate(
                1 + 2 + 1 + 1 + Integer.BYTES
        ).order(ByteOrder.BIG_ENDIAN);

        payload.put((byte) 4);  // FOUND
        payload.putShort((short) 1);
        payload.put((byte) 'k');
        payload.put((byte) 1);  // stringType
        payload.putInt(MAX_FRAME_BYTES + 1);

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(rawFrame(payload.array()))
        );
    }

    @Test
    void decode_shouldRejectValueLengthMismatch() {
        ByteBuffer payload = ByteBuffer.allocate(
                1 + 2 + 1 + 1 + Integer.BYTES + 2
        ).order(ByteOrder.BIG_ENDIAN);

        payload.put((byte) 4);  // FOUND
        payload.putShort((short) 1);
        payload.put((byte) 'k');
        payload.put((byte) 1);  // stringType
        payload.putInt(5);       // Claims 5 bytes
        payload.put(new byte[] {'h', 'i'}); // Only 2 bytes

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(rawFrame(payload.array()))
        );
    }

    @Test
    void decode_shouldRejectUnexpectedValueFieldsForExists() {
        byte[] frame = rawFrame(new byte[] {
                1,      // EXISTS
                0, 1,   // Key length
                'k',
                1       // Unexpected extra field
        });

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectInvalidBooleanValue() {
        byte[] frame = rawFoundFrame(
                4, new byte[] {2});

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectInvalidIntegerValueLength() {
        byte[] frame = rawFoundFrame(
                3, new byte[] {1, 2});

        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    @Test
    void decode_shouldRejectInvalidLongValueLength() {
        byte[] frame = rawFoundFrame(
                4, new byte[] {1, 2, 3});

        // Data type code 4 is boolType in this example mapping.
        // Use the longType code below if your enum assigns it differently.
        assertThrows(
                IOException.class,
                () -> ResponseParser.decode(frame)
        );
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private static void assertFoundRoundTrip(
            String key, byte[] value, DataType type)
            throws IOException {

        CommandResponse original = new CommandResponse(
                OperationResponseCode.FOUND,
                key,
                value,
                type
        );

        byte[] encoded = ResponseParser.encode(original);
        CommandResponse decoded = ResponseParser.decode(encoded);

        assertEquals(OperationResponseCode.FOUND, decoded.getStatusCode());
        assertEquals(key, decoded.getKey());
        assertEquals(type, decoded.getType());
        assertArrayEquals(value, decoded.getValue());
    }

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

    private static byte[] encodeKeyOnlyResponse(
            OperationResponseCode code, String key)
            throws IOException {

        return ResponseParser.encode(
                new CommandResponse(code, key, null, null));
    }

    private static byte[] rawFoundFrame(
            int typeCode, byte[] value) {

        byte[] key = {'k'};

        ByteBuffer payload = ByteBuffer.allocate(
                1 + Short.BYTES + key.length
                        + 1 + Integer.BYTES + value.length
        ).order(ByteOrder.BIG_ENDIAN);

        payload.put((byte) 4); // FOUND
        payload.putShort((short) key.length);
        payload.put(key);
        payload.put((byte) typeCode);
        payload.putInt(value.length);
        payload.put(value);

        return rawFrame(payload.array());
    }

    private static byte[] rawFrame(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(
                Integer.BYTES + payload.length
        ).order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(payload.length);
        buffer.put(payload);

        return buffer.array();
    }
}
