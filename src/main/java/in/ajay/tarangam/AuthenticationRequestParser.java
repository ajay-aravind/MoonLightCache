
package in.ajay.tarangam;

import in.ajay.core.AuthenticationRequest;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class AuthenticationRequestParser {

    private static final int LENGTH_PREFIX_BYTES = 4;
    private static final int MAX_USERNAME_BYTES = 1024;
    private static final int MAX_PASSWORD_BYTES = 4096;
    private static final int MAX_FRAME_BYTES =
            2 + MAX_USERNAME_BYTES + 2 + MAX_PASSWORD_BYTES;

    private AuthenticationRequestParser() {
    }

    public static byte[] encode(AuthenticationRequest request)
            throws IOException {

        if (request == null ||
                request.getUserName() == null ||
                request.getPassword() == null) {
            throw new IOException("Invalid authentication request");
        }

        byte[] username = request.getUserName()
                .getBytes(StandardCharsets.UTF_8);

        byte[] password = request.getPassword()
                .getBytes(StandardCharsets.UTF_8);

        if (username.length == 0 ||
                username.length > MAX_USERNAME_BYTES) {
            throw new IOException("Invalid username length");
        }

        if (password.length == 0 ||
                password.length > MAX_PASSWORD_BYTES) {
            throw new IOException("Invalid password length");
        }

        int frameLength = 2 + username.length
                + 2 + password.length;

        ByteBuffer buffer = ByteBuffer.allocate(
                LENGTH_PREFIX_BYTES + frameLength
        ).order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(frameLength);
        buffer.putShort((short) username.length);
        buffer.put(username);
        buffer.putShort((short) password.length);
        buffer.put(password);

        return buffer.array();
    }

    public static AuthenticationRequest decode(byte[] frame)
            throws IOException {

        if (frame == null ||
                frame.length < 4 ||
                frame.length > MAX_FRAME_BYTES) {
            throw new IOException("Invalid authentication frame length");
        }

        ByteBuffer buffer = ByteBuffer.wrap(frame)
                .order(ByteOrder.BIG_ENDIAN);

        int usernameLength = Short.toUnsignedInt(buffer.getShort());

        if (usernameLength == 0 ||
                usernameLength > MAX_USERNAME_BYTES ||
                buffer.remaining() < usernameLength + 2) {
            throw new IOException("Invalid username length");
        }

        byte[] usernameBytes = new byte[usernameLength];
        buffer.get(usernameBytes);

        int passwordLength = Short.toUnsignedInt(buffer.getShort());

        if (passwordLength == 0 ||
                passwordLength > MAX_PASSWORD_BYTES ||
                buffer.remaining() != passwordLength) {
            throw new IOException("Invalid password length");
        }

        byte[] passwordBytes = new byte[passwordLength];
        buffer.get(passwordBytes);

        String username = new String(
                usernameBytes, StandardCharsets.UTF_8);

        String password = new String(
                passwordBytes, StandardCharsets.UTF_8);

        return new AuthenticationRequest(username, password);
    }

    public static AuthenticationRequest read(InputStream input)
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

        if (frameLength < 5 || frameLength > MAX_FRAME_BYTES) {
            throw new IOException("Invalid authentication frame length: "
                    + frameLength);
        }

        byte[] frame = new byte[frameLength];

        readExactly(input, frame, 0, frameLength);

        return decode(frame);
    }

    public static void write(
            OutputStream output,
            AuthenticationRequest request) throws IOException {

        output.write(encode(request));
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
                        "Unexpected EOF while reading authentication frame");
            }

            if (count == 0) {
                continue;
            }

            totalRead += count;
        }
    }
}
