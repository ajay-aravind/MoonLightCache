package in.ajay.tarangam;

import in.ajay.core.CommandResponse;
import in.ajay.core.DataType;
import in.ajay.core.OperationResponseCode;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ResponseParser {

    /*
     * Encodes one complete command response.
     * Layout:
     * [4-byte frame length]
     * [1-byte commmand response code]
     * [2-byte key length][key bytes]
     * GET only: [1-byte data type][4-byte value length][value bytes]
     * */


    private ResponseParser() {}

    private static final int LENGTH_PREFIX_BYTES = Integer.BYTES;
    private static final int MAX_FRAME_BYTES = 1024 * 1024;
    private static final int MAX_KEY_BYTES = 65_535;
    private static final int MAX_VALUE_BYTES = 1024 * 1024;

    /**
     * Layout:
     * [4-byte frame length]
     * [1-byte response code]
     * [2-byte key length][key bytes]
     * FOUND only: [1-byte data type][4-byte value length][value bytes]
     *
     * Frame length excludes the 4-byte length prefix.
     */
    public static byte[] encode(CommandResponse response)
            throws IOException {

        Objects.requireNonNull(response, "response");

        OperationResponseCode code =
                Objects.requireNonNull(
                        response.getStatusCode(), "responseCode");

        String key = Objects.requireNonNull(response.getKey(), "key");
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);

        if (keyBytes.length == 0 || keyBytes.length > MAX_KEY_BYTES) {
            throw new IOException(
                    "Invalid key length: " + keyBytes.length);
        }

        byte[] value = response.getValue();
        DataType type = response.getType();

        int payloadLength = 1 + Short.BYTES + keyBytes.length;

        if (code == OperationResponseCode.FOUND || code == OperationResponseCode.CREATED) {
            if (type == null) {
                throw new IOException("FOUND/CREATED response requires a data type");
            }
            if (value == null) {
                throw new IOException("FOUND/CREATED response requires a value");
            }
            if (value.length > MAX_VALUE_BYTES) {
                throw new IOException("Value exceeds maximum allowed size");
            }

            validateValue(type, value);

            payloadLength += 1 + Integer.BYTES + value.length;
        } else if (type != null || value != null) {
            throw new IOException(
                    code + " response must not contain value fields");
        }

        if (payloadLength > MAX_FRAME_BYTES) {
            throw new IOException("Frame exceeds maximum allowed size");
        }

        ByteBuffer buffer = ByteBuffer
                .allocate(LENGTH_PREFIX_BYTES + payloadLength)
                .order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(payloadLength);
        buffer.put((byte)code.code());
        buffer.putShort((short) keyBytes.length);
        buffer.put(keyBytes);

        if (code == OperationResponseCode.FOUND || code == OperationResponseCode.CREATED) {
            buffer.put((byte) type.code());
            buffer.putInt(value.length);
            buffer.put(value);
        }

        return buffer.array();
    }

    /**
     * Decodes exactly one complete response frame.
     */
    public static CommandResponse decode(byte[] frame)
            throws IOException {

        Objects.requireNonNull(frame, "frame");

        if (frame.length < LENGTH_PREFIX_BYTES) {
            throw new EOFException("Incomplete length prefix");
        }

        ByteBuffer buffer = ByteBuffer.wrap(frame)
                .order(ByteOrder.BIG_ENDIAN);

        long payloadLength =
                Integer.toUnsignedLong(buffer.getInt());

        if (payloadLength > MAX_FRAME_BYTES) {
            throw new IOException("Frame exceeds maximum allowed size");
        }

        if (payloadLength != frame.length - LENGTH_PREFIX_BYTES) {
            throw new IOException(
                    "Frame length mismatch: declared=" + payloadLength
                            + ", actual="
                            + (frame.length - LENGTH_PREFIX_BYTES));
        }

        if (payloadLength < 1 + Short.BYTES) {
            throw new IOException("Frame is too short");
        }

        OperationResponseCode code = OperationResponseCode.fromCode(
                Byte.toUnsignedInt(buffer.get()));

        int keyLength = Short.toUnsignedInt(buffer.getShort());

        if (keyLength == 0 || keyLength > MAX_KEY_BYTES) {
            throw new IOException("Invalid key length: " + keyLength);
        }

        if (buffer.remaining() < keyLength) {
            throw new IOException("Truncated key");
        }

        byte[] keyBytes = new byte[keyLength];
        buffer.get(keyBytes);

        String key = new String(keyBytes, StandardCharsets.UTF_8);

        DataType type = null;
        byte[] value = null;

        if (code == OperationResponseCode.FOUND) {
            if (buffer.remaining() < 1 + Integer.BYTES) {
                throw new IOException("Truncated FOUND response");
            }

            type = DataType.fromCode(
                    Byte.toUnsignedInt(buffer.get()));

            long valueLength =
                    Integer.toUnsignedLong(buffer.getInt());

            if (valueLength > MAX_VALUE_BYTES) {
                throw new IOException(
                        "Value exceeds maximum allowed size: "
                                + valueLength);
            }

            if (buffer.remaining() != valueLength) {
                throw new IOException(
                        "Value length does not match frame length");
            }

            value = new byte[(int) valueLength];
            buffer.get(value);

            validateValue(type, value);
        } else if (buffer.hasRemaining()) {
            throw new IOException(
                    code + " response contains unexpected fields");
        }

        try {
            return new CommandResponse(code, key, value, type);
        } catch (IllegalArgumentException e) {
            throw new IOException(
                    "Invalid response: " + e.getMessage(), e);
        }
    }

    private static void validateValue(DataType type, byte[] value)
            throws IOException {

        switch (type) {
            case stringType -> {
                // UTF-8 encoded string; variable length.
            }
            case integerType -> {
                if (value.length != Integer.BYTES) {
                    throw new IOException("integerType requires 4 bytes");
                }
            }
            case longType -> {
                if (value.length != Long.BYTES) {
                    throw new IOException("longType requires 8 bytes");
                }
            }
            case boolType -> {
                if (value.length != 1
                        || (value[0] != 0 && value[0] != 1)) {
                    throw new IOException(
                            "boolType requires one byte: 0 or 1");
                }
            }
            case characterType -> {
                if (value.length != Character.BYTES) {
                    throw new IOException(
                            "characterType requires 2 bytes");
                }
            }
        }
    }

    /**
     * Reads one complete response from a TCP stream.
     */
    public static CommandResponse read(InputStream input)
            throws IOException {

        Objects.requireNonNull(input, "input");

        byte[] prefix = readExactly(input, LENGTH_PREFIX_BYTES);

        long payloadLength = Integer.toUnsignedLong(
                ByteBuffer.wrap(prefix)
                        .order(ByteOrder.BIG_ENDIAN)
                        .getInt());

        if (payloadLength > MAX_FRAME_BYTES) {
            throw new IOException("Frame exceeds maximum allowed size");
        }

        byte[] payload = readExactly(input, (int) payloadLength);
        byte[] frame = new byte[LENGTH_PREFIX_BYTES + payload.length];

        System.arraycopy(prefix, 0, frame, 0, prefix.length);
        System.arraycopy(payload, 0, frame, prefix.length, payload.length);

        return decode(frame);
    }

    public static void write(
            OutputStream output, CommandResponse response)
            throws IOException {

        Objects.requireNonNull(output, "output");
        output.write(encode(response));
        output.flush();
    }

    private static byte[] readExactly(InputStream input, int length)
            throws IOException {

        byte[] result = new byte[length];
        int offset = 0;

        while (offset < length) {
            int count = input.read(result, offset, length - offset);

            if (count == -1) {
                throw new EOFException("Connection closed mid-frame");
            }

            if (count == 0) {
                int oneByte = input.read();

                if (oneByte == -1) {
                    throw new EOFException("Connection closed mid-frame");
                }

                result[offset++] = (byte) oneByte;
            } else {
                offset += count;
            }
        }

        return result;
    }
}
