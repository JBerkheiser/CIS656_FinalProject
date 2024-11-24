package server;

import java.io.*;
import java.net.*;
import java.util.logging.*;

class PeerHandler implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(PeerHandler.class.getName());

    static {
        initializeLogger();
    }

    private final Socket peerSocket;

    // Constructor
    public PeerHandler(Socket peerSocket) {
        this.peerSocket = peerSocket;
    }

    /**
     * Handles client connections and interactions for each peer.
     * Registers the peer with the central server, provides it with a random
     * peer for connection (if available), and listens for commands from the peer.
     * Supported commands include:
     * - "quit": Removes the peer from the network and terminates the connection.
     */
    @Override
    public void run() {
        InetSocketAddress peerAddress = new InetSocketAddress(peerSocket.getInetAddress(), peerSocket.getPort());

        try (BufferedReader input = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()))) {

            // Read the peer's listener port from the input stream
            String portLine = input.readLine();
            int peerListenerPort = Integer.parseInt(portLine);

            // Registering the peer with the Central Server
            Central_Server.addPeer(peerSocket, peerListenerPort);
            LOGGER.info("Peer joined: " + peerAddress + " (is listening on port " + peerListenerPort + ") for peer connections");

            // Provide a random peer's listener port or status message
            PeerInfoRecord randomPeer = Central_Server.getRandomPeer(peerAddress);
            if (randomPeer != null) {
                // Validate the random peer's details
                if (randomPeer.getPeerListenerPort() <= 0 || randomPeer.getPeerListenerPort() > 65535) {
                    LOGGER.warning("Invalid random peer retrieved: " + randomPeer);
                    output.write("Error: Invalid peer details.\n");
                } else {
                    // Send the random peer's listener port to the connecting peer
                    String connectMessage = "Connect to: " + randomPeer.getAddress().getHostAddress()+ " " + randomPeer.getPeerListenerPort();
                    output.write(connectMessage + "\n");
                    LOGGER.info("Sent random peer to " + peerAddress + ": " + connectMessage);
                }
            } else {
                // No other peers available
                output.write("You are the first peer in the network.\n");
                LOGGER.info("First peer in the network: " + peerAddress);
            }
            output.flush();

            // Handle commands from the peer
            String command;
            while ((command = input.readLine()) != null) {
                LOGGER.info("Received command from " + peerAddress + ": " + command);

                if (command.equalsIgnoreCase("quit")) {
                    LOGGER.info("Peer disconnecting: " + peerAddress);
                    Central_Server.removePeer(peerAddress);
                    break;
                } else {
                    LOGGER.warning("Unknown command from peer: " + command);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error handling peer: " + peerAddress, e);
        } finally {
            cleanup(peerAddress);
        }
    }

    /**
     * Cleans up the connection and removes the peer from the network.
     *
     * @param peerAddress The address of the peer to clean up.
     */
    private void cleanup(InetSocketAddress peerAddress) {
        try {
            Central_Server.removePeer(peerAddress);
            peerSocket.close();
            LOGGER.info("Closed connection for peer: " + peerAddress);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing client socket for peer: " + peerAddress, e);
        }
    }

    /**
     * Initializes the logger for the PeerHandler class.
     */
    private static void initializeLogger() {
        try {
            FileHandler fileHandler = new FileHandler("server-logs.txt", true); // Append mode
            fileHandler.setFormatter(new SimpleFormatter());
            LOGGER.addHandler(fileHandler);

            Logger rootLogger = Logger.getLogger("");
            rootLogger.removeHandler(rootLogger.getHandlers()[0]); // Removes logging in the server console

            LOGGER.setLevel(Level.INFO);
        } catch (IOException e) {
            System.err.println("Failed to set up file handler for logger: " + e.getMessage());
        }
    }
}