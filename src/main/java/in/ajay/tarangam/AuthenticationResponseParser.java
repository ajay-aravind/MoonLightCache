
package in.ajay.tarangam;

import in.ajay.core.AuthenticationResponse;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class AuthenticationResponseParser {

    private static final int LENGTH_PREFIX_BYTES = 4;
    private static final int FRAME_LENGTH = 1;

    private AuthenticationResponseParser() {
    }

    public static byte[] encode(AuthenticationResponse response)
            throws IOException {

        if (response == null) {
            throw new IOException("Authentication response cannot be null");
        }

        ByteBuffer buffer = ByteBuffer.allocate(
                LENGTH_PREFIX_BYTES + FRAME_LENGTH
        ).order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(FRAME_LENGTH);
        buffer.put((byte) response.code());

        return buffer.array();
    }

    public static AuthenticationResponse decode(byte[] frame)
            throws IOException {

        if (frame == null || frame.length != FRAME_LENGTH) {
            throw new IOException("Invalid authentication response frame");
        }

        int code = Byte.toUnsignedInt(frame[0]);

        return AuthenticationResponse.fromCode(code);
    }

    public static AuthenticationResponse read(InputStream input)
            throws IOException {

        int firstByte = input.read();

        if (firstByte == -1) {
            return null;
        }

        byte[] remainingPrefix = new byte[3];
        readExactly(input, remainingPrefix, 0, 3);

        int frameLength =
                (firstByte << 24)
                        | ((remainingPrefix[0] & 0xFF) << 16)
                        | ((remainingPrefix[1] & 0xFF) << 8)
                        | (remainingPrefix[2] & 0xFF);

        if (frameLength != FRAME_LENGTH) {
            throw new IOException(
                    "Invalid authentication response frame length: "
                            + frameLength);
        }

        byte[] frame = new byte[FRAME_LENGTH];

        readExactly(input, frame, 0, FRAME_LENGTH);

        return decode(frame);
    }

    public static void write(
            OutputStream output,
            AuthenticationResponse response) throws IOException {

        output.write(encode(response));
        output.flush();
    }

    private static void readExactly(
            InputStream input,
            byte[] buffer,
            int offset,
            int length) throws IOException {

        int totalRead = 0;

        while (totalRead < length) {
            int count = input.read(
                    buffer, offset + totalRead, length - totalRead);

            if (count == -1) {
                throw new EOFException(
                        "Unexpected EOF while reading authentication response");
            }

            if (count == 0) {
                continue;
            }

            totalRead += count;
        }
    }
}
