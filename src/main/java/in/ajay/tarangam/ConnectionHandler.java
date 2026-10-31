package in.ajay.tarangam;

import in.ajay.core.*;
import in.ajay.logging.ILogger;

import javax.net.ssl.SSLSocket;
import java.io.EOFException;
import java.io.IOException;

public class ConnectionHandler {

    private final SSLSocket connection;
    private ILogger logger;
    private MoonLightCache cache;

    public ConnectionHandler(SSLSocket socket, ILogger logger, MoonLightCache cache) {
        this.connection = socket;
        this.logger = logger;
        this.cache = cache;
    }

    void handleClient() {
        try (this.connection) {
            connection.setSoTimeout(30_000);

            logger.LogInfo("Starting handshake with client:" + connection.getRemoteSocketAddress());
            connection.startHandshake();
            logger.LogInfo("handshake completed with client:" + connection.getRemoteSocketAddress());

            logger.LogDebug("Reading username and password" + connection.getRemoteSocketAddress());

            AuthenticationRequest authRequest = AuthenticationRequestParser.read(this.connection.getInputStream());
            if(authRequest != null) {
                boolean isAuthenticationSuccessful = new Authenticator().authenticate(authRequest);

                if (isAuthenticationSuccessful) {

                    logger.LogDebug("Authentication is successful for client: " + connection.getRemoteSocketAddress());
                    AuthenticationResponse authResponse = AuthenticationResponse.SUCCESSFUL;
                    AuthenticationResponseParser.write(this.connection.getOutputStream(), authResponse);

                    logger.LogInfo("Auth response sent. \n Handling client:" + connection.getRemoteSocketAddress());
                    while (!connection.isClosed()) {
                        try {
                            Command command = RequestParser.read(connection.getInputStream());
                            if (command != null) {
                                logger.LogDebug(command.toString());
                                CommandResponse response = cache.ProcessCommand(command);
                                logger.LogDebug(response.toString());
                                ResponseParser.write(connection.getOutputStream(), response);
                            } else {
                                break;
                            }
                        } catch (EOFException e) {
                            logger.LogDebug("client close the connection:" + e.getMessage());
                            break;
                        }
                    }
                } else {
                    this.logger.LogInfo("Client authentication failed. Invalid username or password. Closing connection");
                }
            }
            else {
                this.logger.LogInfo("Client authentication failed. Null frame received. Closing connection");
            }
        } catch (IOException e) {
            logger.LogDebug("Client connection error "+connection.getRemoteSocketAddress() + e.getMessage());
        } catch (Exception e) {
            logger.LogDebug("Exception while handling client request:" + e.getMessage());
        }
    }
}
