package in.ajay.test;

import in.ajay.core.AuthenticationResponse;
import in.ajay.tarangam.AuthenticationResponseParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationResponseParserTest {

    @Test
    void encodeAndDecodeSuccessfulResponse() throws IOException {
        byte[] encoded = AuthenticationResponseParser.encode(
                AuthenticationResponse.SUCCESSFUL);

        byte[] payload = {
                encoded[4]
        };

        AuthenticationResponse decoded =
                AuthenticationResponseParser.decode(payload);

        assertEquals(AuthenticationResponse.SUCCESSFUL, decoded);
    }

    @Test
    void encodeAndDecodeUnsuccessfulResponse() throws IOException {
        byte[] encoded = AuthenticationResponseParser.encode(
                AuthenticationResponse.UNSUCCESSFUL);

        byte[] payload = {
                encoded[4]
        };

        AuthenticationResponse decoded =
                AuthenticationResponseParser.decode(payload);

        assertEquals(AuthenticationResponse.UNSUCCESSFUL, decoded);
    }

    @Test
    void encodeShouldUseCorrectBinaryFormat() throws IOException {
        byte[] encoded = AuthenticationResponseParser.encode(
                AuthenticationResponse.SUCCESSFUL);

        assertEquals(5, encoded.length);

        ByteBuffer buffer = ByteBuffer.wrap(encoded)
                .order(ByteOrder.BIG_ENDIAN);

        assertEquals(1, buffer.getInt());
        assertEquals(1, Byte.toUnsignedInt(buffer.get()));
        assertFalse(buffer.hasRemaining());
    }

    @Test
    void encodeShouldRejectNullResponse() {
        assertThrows(IOException.class,
                () -> AuthenticationResponseParser.encode(null));
    }

    @Test
    void decodeShouldRejectNullFrame() {
        assertThrows(IOException.class,
                () -> AuthenticationResponseParser.decode(null));
    }

    @Test
    void decodeShouldRejectEmptyFrame() {
        assertThrows(IOException.class,
                () -> AuthenticationResponseParser.decode(new byte[0]));
    }

    @Test
    void decodeShouldRejectFrameWithMultipleBytes() {
        byte[] payload = {1, 2};

        assertThrows(IOException.class,
                () -> AuthenticationResponseParser.decode(payload));
    }

    @Test
    void decodeShouldRejectUnknownResponseCode() {
        byte[] payload = {99};

        assertThrows(IOException.class,
                () -> AuthenticationResponseParser.decode(payload));
    }

    @Test
    void readShouldReadSuccessfulResponse() throws IOException {
        byte[] encoded = AuthenticationResponseParser.encode(
                AuthenticationResponse.SUCCESSFUL);

        ByteArrayInputStream input =
                new ByteArrayInputStream(encoded);

        AuthenticationResponse response =
                AuthenticationResponseParser.read(input);

        assertEquals(AuthenticationResponse.SUCCESSFUL, response);
    }

    @Test
    void readShouldReadUnsuccessfulResponse() throws IOException {
        byte[] encoded = AuthenticationResponseParser.encode(
                AuthenticationResponse.UNSUCCESSFUL);

        ByteArrayInputStream input =
                new ByteArrayInputStream(encoded);

        AuthenticationResponse response =
                AuthenticationResponseParser.read(input);

        assertEquals(AuthenticationResponse.UNSUCCESSFUL, response);
    }

    @Test
    void writeShouldWriteSuccessfulResponse() throws IOException {
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        AuthenticationResponseParser.write(
                output, AuthenticationResponse.SUCCESSFUL);

        assertArrayEquals(
                AuthenticationResponseParser.encode(
                        AuthenticationResponse.SUCCESSFUL),
                output.toByteArray());
    }

    @Test
    void writeShouldWriteUnsuccessfulResponse() throws IOException {
        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        AuthenticationResponseParser.write(
                output, AuthenticationResponse.UNSUCCESSFUL);

        assertArrayEquals(
                AuthenticationResponseParser.encode(
                        AuthenticationResponse.UNSUCCESSFUL),
                output.toByteArray());
    }

    @Test
    void readShouldReturnNullOnCleanEof() throws IOException {
        ByteArrayInputStream input =
                new ByteArrayInputStream(new byte[0]);

        assertNull(AuthenticationResponseParser.read(input));
    }

    @Test
    void readShouldThrowOnIncompleteLengthPrefix() {
        byte[] inputBytes = {0, 0};

        ByteArrayInputStream input =
                new ByteArrayInputStream(inputBytes);

        assertThrows(EOFException.class,
                () -> AuthenticationResponseParser.read(input));
    }

    @Test
    void readShouldThrowOnIncompletePayload() {
        // Declared payload length = 1, but payload is missing.
        byte[] inputBytes = {0, 0, 0, 1};

        ByteArrayInputStream input =
                new ByteArrayInputStream(inputBytes);

        assertThrows(EOFException.class,
                () -> AuthenticationResponseParser.read(input));
    }

    @Test
    void readShouldRejectInvalidFrameLength() {
        // Response payload must be exactly 1 byte.
        byte[] inputBytes = {
                0, 0, 0, 2,
                1, 2
        };

        ByteArrayInputStream input =
                new ByteArrayInputStream(inputBytes);

        assertThrows(IOException.class,
                () -> AuthenticationResponseParser.read(input));
    }

    @Test
    void readShouldRejectUnknownResponseCode() {
        byte[] inputBytes = {
                0, 0, 0, 1,
                99
        };

        ByteArrayInputStream input =
                new ByteArrayInputStream(inputBytes);

        assertThrows(IOException.class,
                () -> AuthenticationResponseParser.read(input));
    }

    @Test
    void readShouldSupportMultipleResponsesOnSameStream()
            throws IOException {

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        AuthenticationResponseParser.write(
                output, AuthenticationResponse.SUCCESSFUL);

        AuthenticationResponseParser.write(
                output, AuthenticationResponse.UNSUCCESSFUL);

        ByteArrayInputStream input =
                new ByteArrayInputStream(output.toByteArray());

        assertEquals(
                AuthenticationResponse.SUCCESSFUL,
                AuthenticationResponseParser.read(input));

        assertEquals(
                AuthenticationResponse.UNSUCCESSFUL,
                AuthenticationResponseParser.read(input));

        assertNull(AuthenticationResponseParser.read(input));
    }
}
