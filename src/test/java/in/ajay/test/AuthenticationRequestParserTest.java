package in.ajay.test;

import in.ajay.core.AuthenticationRequest;
import in.ajay.tarangam.AuthenticationRequestParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationRequestParserTest {

    @Test
    void encodeAndDecodeShouldWork() throws IOException {
        AuthenticationRequest request =
                new AuthenticationRequest("admin", "secret123");

        byte[] encoded = AuthenticationRequestParser.encode(request);

        // Remove the 4-byte frame length prefix.
        byte[] payload = new byte[encoded.length - 4];
        System.arraycopy(encoded, 4, payload, 0, payload.length);

        AuthenticationRequest decoded =
                AuthenticationRequestParser.decode(payload);

        assertEquals(request.getUserName(), decoded.getUserName());
        assertEquals(request.getPassword(), decoded.getPassword());
    }

    @Test
    void encodeShouldUseCorrectBinaryFormat() throws IOException {
        AuthenticationRequest request =
                new AuthenticationRequest("admin", "pass");

        byte[] encoded = AuthenticationRequestParser.encode(request);

        ByteBuffer buffer = ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN);

        int frameLength = buffer.getInt();

        assertEquals(encoded.length - 4, frameLength);

        int usernameLength =
                Short.toUnsignedInt(buffer.getShort());

        assertEquals(5, usernameLength);

        byte[] username = new byte[usernameLength];
        buffer.get(username);

        assertEquals("admin",
                new String(username, StandardCharsets.UTF_8));

        int passwordLength =
                Short.toUnsignedInt(buffer.getShort());

        assertEquals(4, passwordLength);

        byte[] password = new byte[passwordLength];
        buffer.get(password);

        assertEquals("pass",
                new String(password, StandardCharsets.UTF_8));

        assertFalse(buffer.hasRemaining());
    }

    @Test
    void encodeShouldSupportUtf8Characters() throws IOException {
        AuthenticationRequest request =
                new AuthenticationRequest("用户", "密码123");

        byte[] encoded = AuthenticationRequestParser.encode(request);

        byte[] payload = new byte[encoded.length - 4];
        System.arraycopy(encoded, 4, payload, 0, payload.length);

        AuthenticationRequest decoded =
                AuthenticationRequestParser.decode(payload);

        assertEquals(request.getUserName(), decoded.getUserName());
        assertEquals(request.getPassword(), decoded.getPassword());
    }

    @Test
    void encodeShouldRejectNullRequest() {
        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.encode(null));
    }

    @Test
    void encodeShouldRejectNullUsername() {
        AuthenticationRequest request =
                new AuthenticationRequest(null, "password");

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.encode(request));
    }

    @Test
    void encodeShouldRejectNullPassword() {
        AuthenticationRequest request =
                new AuthenticationRequest("admin", null);

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.encode(request));
    }

    @Test
    void encodeShouldRejectEmptyUsername() {
        AuthenticationRequest request =
                new AuthenticationRequest("", "password");

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.encode(request));
    }

    @Test
    void encodeShouldRejectEmptyPassword() {
        AuthenticationRequest request =
                new AuthenticationRequest("admin", "");

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.encode(request));
    }

    @Test
    void decodeShouldRejectNullFrame() {
        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.decode(null));
    }

    @Test
    void decodeShouldRejectEmptyFrame() {
        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.decode(new byte[0]));
    }

    @Test
    void decodeShouldRejectInvalidUsernameLength() {
        // Username length = 0.
        byte[] payload = {
                0, 0,
                0, 1, 65
        };

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.decode(payload));
    }

    @Test
    void decodeShouldRejectUsernameLengthExceedingAvailableBytes() {
        // Claims username length = 5, but only 2 bytes are available.
        byte[] payload = {
                0, 5,
                65, 66
        };

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.decode(payload));
    }

    @Test
    void decodeShouldRejectEmptyPassword() {
        byte[] payload = {
                0, 1, 65,  // username = A
                0, 0       // empty password
        };

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.decode(payload));
    }

    @Test
    void decodeShouldRejectPasswordLengthExceedingAvailableBytes() {
        byte[] payload = {
                0, 1, 65,  // username = A
                0, 5,      // password length = 5
                66, 67     // only 2 bytes
        };

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.decode(payload));
    }

    @Test
    void decodeShouldRejectTrailingBytes() {
        byte[] payload = {
                0, 1, 65,
                0, 1, 66,
                99          // unexpected trailing byte
        };

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.decode(payload));
    }

    @Test
    void readShouldReadEncodedRequest() throws IOException {
        AuthenticationRequest request =
                new AuthenticationRequest("admin", "secret123");

        byte[] encoded = AuthenticationRequestParser.encode(request);

        ByteArrayInputStream input =
                new ByteArrayInputStream(encoded);

        AuthenticationRequest decoded =
                AuthenticationRequestParser.read(input);

        assertNotNull(decoded);
        assertEquals("admin", decoded.getUserName());
        assertEquals("secret123", decoded.getPassword());
    }

    @Test
    void writeShouldWriteEncodedRequest() throws IOException {
        AuthenticationRequest request =
                new AuthenticationRequest("admin", "secret123");

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        AuthenticationRequestParser.write(output, request);

        byte[] written = output.toByteArray();
        byte[] expected = AuthenticationRequestParser.encode(request);

        assertArrayEquals(expected, written);
    }

    @Test
    void readShouldReturnNullOnCleanEof() throws IOException {
        ByteArrayInputStream input =
                new ByteArrayInputStream(new byte[0]);

        assertNull(AuthenticationRequestParser.read(input));
    }

    @Test
    void readShouldThrowOnIncompleteLengthPrefix() {
        byte[] inputBytes = {0, 0};

        ByteArrayInputStream input =
                new ByteArrayInputStream(inputBytes);

        assertThrows(EOFException.class,
                () -> AuthenticationRequestParser.read(input));
    }

    @Test
    void readShouldThrowOnIncompletePayload() throws IOException {
        // Declared payload length = 5, but only 2 payload bytes exist.
        byte[] inputBytes = {
                0, 0, 0, 5,
                0, 1
        };

        ByteArrayInputStream input =
                new ByteArrayInputStream(inputBytes);

        assertThrows(EOFException.class,
                () -> AuthenticationRequestParser.read(input));

        byte[] inputBytes2 = {0, 5, 65, 66};

        ByteArrayInputStream input2 =
                new ByteArrayInputStream(inputBytes2);

        assertThrows(EOFException.class,
                () -> AuthenticationRequestParser.read(input2));
    }

    @Test
    void readShouldRejectFrameLengthBelowMinimum() {
        // Frame length = 4, minimum valid request payload is 5.
        byte[] inputBytes = {
                0, 0, 0, 4,
                0, 0, 0, 0
        };

        ByteArrayInputStream input =
                new ByteArrayInputStream(inputBytes);

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.read(input));
    }

    @Test
    void readShouldSupportMultipleRequestsOnSameStream()
            throws IOException {

        AuthenticationRequest first =
                new AuthenticationRequest("admin", "password1");

        AuthenticationRequest second =
                new AuthenticationRequest("user", "password2");

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        AuthenticationRequestParser.write(output, first);
        AuthenticationRequestParser.write(output, second);

        ByteArrayInputStream input =
                new ByteArrayInputStream(output.toByteArray());

        AuthenticationRequest decodedFirst =
                AuthenticationRequestParser.read(input);

        AuthenticationRequest decodedSecond =
                AuthenticationRequestParser.read(input);

        assertEquals("admin", decodedFirst.getUserName());
        assertEquals("password1", decodedFirst.getPassword());

        assertEquals("user", decodedSecond.getUserName());
        assertEquals("password2", decodedSecond.getPassword());

        assertNull(AuthenticationRequestParser.read(input));
    }

    @Test
    void encodeShouldSupportMaximumUsernameLength() throws IOException {
        String username = "a".repeat(1024);

        AuthenticationRequest request =
                new AuthenticationRequest(username, "password");

        assertDoesNotThrow(
                () -> AuthenticationRequestParser.encode(request));
    }

    @Test
    void encodeShouldRejectUsernameExceedingMaximumLength() {
        String username = "a".repeat(1025);

        AuthenticationRequest request =
                new AuthenticationRequest(username, "password");

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.encode(request));
    }

    @Test
    void encodeShouldSupportMaximumPasswordLength() throws IOException {
        String password = "a".repeat(4096);

        AuthenticationRequest request =
                new AuthenticationRequest("admin", password);

        assertDoesNotThrow(
                () -> AuthenticationRequestParser.encode(request));
    }

    @Test
    void encodeShouldRejectPasswordExceedingMaximumLength() {
        String password = "a".repeat(4097);

        AuthenticationRequest request =
                new AuthenticationRequest("admin", password);

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.encode(request));
    }

    @Test
    void decodeShouldRejectIncompleteUsernameLength() {
        byte[] payload = {0};

        assertThrows(IOException.class,
                () -> AuthenticationRequestParser.decode(payload));
    }
}
