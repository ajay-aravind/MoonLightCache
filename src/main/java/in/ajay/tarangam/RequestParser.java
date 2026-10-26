package in.ajay.tarangam;


import in.ajay.core.Command;
import in.ajay.core.DataType;
import in.ajay.core.Operation;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class RequestParser {

    private RequestParser() {}

    // Length prefix is excluded from the length it stores.
    private static final int LENGTH_PREFIX_BYTES = 4;
    private static final int MAX_FRAME_BYTES = 1024 * 1024;
    private static final int MAX_KEY_BYTES = 65_535;
    private static final int MAX_VALUE_BYTES = 1024 * 1024;

    /**
     * Encodes one complete message.
     *
     * Layout:
     * [4-byte frame length]
     * [1-byte command]
     * [2-byte key length][key bytes]
     * SET only: [1-byte data type][4-byte value length][value bytes][8-byte TTL]
     */
    public static byte[] encode(Command message) throws IOException {
        Objects.requireNonNull(message, "message");

        byte[] keyBytes = message.getKey().getBytes(StandardCharsets.UTF_8);

        if(keyBytes.length > MAX_KEY_BYTES){
            throw new IOException("key length exceeds maximum allowed size");
        }
        byte[] value = message.getValue();

        int payloadLength =
                1 + Short.BYTES + keyBytes.length;

        if (message.getOperation() == Operation.SET) {
            payloadLength += 1 + Integer.BYTES + value.length + Long.BYTES;
        }

        if (payloadLength > MAX_FRAME_BYTES) {
            throw new IOException("Frame exceeds maximum allowed size");
        }

        ByteBuffer buffer = ByteBuffer
                .allocate(LENGTH_PREFIX_BYTES + payloadLength)
                .order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(payloadLength);
        buffer.put((byte) message.getOperation().code());
        buffer.putShort((short) keyBytes.length);
        buffer.put(keyBytes);

        if (message.getOperation() == Operation.SET) {
            buffer.put((byte) message.getType().code());
            buffer.putInt(value.length);
            buffer.put(value);
            buffer.putLong(message.getTtl());
        }

        return buffer.array();
    }

    /**
     * Decodes exactly one complete frame.
     */
    public static Command decode(byte[] frame) throws IOException {
        Objects.requireNonNull(frame, "frame");

        if (frame.length < LENGTH_PREFIX_BYTES) {
            throw new EOFException("Incomplete length prefix");
        }

        ByteBuffer buffer = ByteBuffer.wrap(frame)
                .order(ByteOrder.BIG_ENDIAN);

        long payloadLength = Integer.toUnsignedLong(buffer.getInt());

        if (payloadLength > MAX_FRAME_BYTES) {
            throw new IOException("Frame exceeds maximum allowed size");
        }

        if (payloadLength != frame.length - LENGTH_PREFIX_BYTES) {
            throw new IOException(
                    "Frame length mismatch: declared=" + payloadLength
                            + ", actual=" + (frame.length - LENGTH_PREFIX_BYTES));
        }

        if (payloadLength < 1 + Short.BYTES) {
            throw new IOException("Frame is too short");
        }

        Operation operation = Operation.fromCode(
                Byte.toUnsignedInt(buffer.get()));

        int keyLength = Short.toUnsignedInt(buffer.getShort());

        if (keyLength == 0 || keyLength > MAX_KEY_BYTES) {
            throw new IOException("Invalid key length: " + keyLength);
        }

        int minimumRemaining = keyLength;
        if (buffer.remaining() < minimumRemaining) {
            throw new IOException("Truncated key");
        }

        byte[] keyBytes = new byte[keyLength];
        buffer.get(keyBytes);

        String key = new String(keyBytes, StandardCharsets.UTF_8);

        DataType type = null;
        byte[] value = null;
        long ttlMillis = 0;

        if (operation == Operation.SET) {
            if (buffer.remaining() < 1 + Integer.BYTES + Long.BYTES) {
                throw new IOException("Truncated SET message");
            }

            type = DataType.fromCode(
                    Byte.toUnsignedInt(buffer.get()));

            long valueLength = Integer.toUnsignedLong(buffer.getInt());

            if (valueLength > MAX_VALUE_BYTES) {
                throw new IOException(
                        "Value exceeds maximum allowed size: " + valueLength);
            }

            if (buffer.remaining() != valueLength + Long.BYTES) {
                throw new IOException(
                        "SET value length does not match frame length");
            }

            value = new byte[(int) valueLength];
            buffer.get(value);

            if (type == DataType.boolType
                    && value[0] != 0 && value[0] != 1) {
                throw new IOException("Invalid BOOLEAN value");
            }

            try {
                Command.validateValueLength(type, value.length);
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage(), e);
            }

            ttlMillis = buffer.getLong();

            if (ttlMillis < -1) {
                throw new IOException("Invalid TTL: " + ttlMillis);
            }
        } else if (buffer.hasRemaining()) {
            throw new IOException(
                    operation + " message contains unexpected fields");
        }

        try {
            return new Command(operation, key, value, ttlMillis, type);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid message: " + e.getMessage(), e);
        }
    }

    /**
     * Reads one frame from a TCP stream without assuming that a single
     * read() call returns all requested bytes.
     */
    public static Command read(InputStream input) throws IOException {
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
            OutputStream output, Command message) throws IOException {
        Objects.requireNonNull(output, "output");
        output.write(encode(message));
        output.flush();
    }

    private static byte[] readExactly(
            InputStream input, int length) throws IOException {

        byte[] result = new byte[length];
        int offset = 0;

        while (offset < length) {
            int count = input.read(result, offset, length - offset);

            if (count == -1) {
                throw new EOFException("Connection closed mid-frame");
            }

            if (count == 0) {
                int singleByte = input.read();
                if (singleByte == -1) {
                    throw new EOFException("Connection closed mid-frame");
                }
                result[offset++] = (byte) singleByte;
            } else {
                offset += count;
            }
        }

        return result;
    }
}
