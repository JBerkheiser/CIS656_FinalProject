package peer;

import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.concurrent.*;

public class Peer {
    private static final int serverPort = 9090; // Central server port
    //TODO explicitly name the serverIPAddress by prompting the user for input
    private static final String serverIPAddress = "127.0.0.1"; // Central server IP address
    private static int peerPort; // Port for this peer's own server
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10); // Thread pool for peer connections
    private static final ConcurrentHashMap<InetSocketAddress, Socket> neighbors = new ConcurrentHashMap<>();
    private static volatile boolean isConnectedToServer = false; // Track connection status
    private static Socket serverSocket; // The connection to the central server
    private static BufferedReader serverInput;

    //terminal formatting
    private static final String BOLD_UNDERLINE = "\u001B[1;4m";
    private static final String RESET_FORMATTING = "\u001B[0m";

    public static void main(String[] args) {
        try {
            // Allow peer to specify its own port in the terminal
            if (args.length > 0) {
                peerPort = Integer.parseInt(args[0]);
            } else {
                try (ServerSocket tempSocket = new ServerSocket(0)) {
                    peerPort = tempSocket.getLocalPort(); // Dynamically assign port
                }
            }

            // Start the peer's own server
            new Thread(() -> startPeerServer(peerPort)).start();

            //Establish server connection and start peerListener
            connectToServer();

            // Handle user commands
            handleUserCommands();

            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    private static void handleUserCommands() {
        try (BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))) {
            String command;
            while (true) {
                System.out.print("Enter command (neighbors/quit): ");
                command = userInput.readLine();

                if (command == null || command.isBlank()) {
                    System.out.println("Invalid command. Available commands: neighbors, quit");
                    continue;
                }

                switch (command.toLowerCase()) {
                    case "quit":
                        disconnectFromServer();
                        disconnectFromNeighbors();
                        System.out.println("Disconnected from the network.");
                        return;
                    case "neighbors":
                        displayNeighbors();
                        break;
                    default:
                        System.out.println("Unknown command. Available commands: neighbors, quit, server status, reconnect.");
                }
            }
        } catch (IOException e) {
            System.out.println("Error reading user input: " + e.getMessage());
        }
    }

    /**
     * Attempts to connect to the central server.
     */
    private static synchronized void connectToServer() {
        try {
            // Establish connection to the central server
            serverSocket = new Socket(serverIPAddress, serverPort);
            serverInput = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            BufferedWriter serverOutput = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream()));

            isConnectedToServer = true;
            int outgoingPort = serverSocket.getLocalPort();
            System.out.println("Connected to the central server on port: " + BOLD_UNDERLINE + outgoingPort + RESET_FORMATTING);

            // Send the peer's listener port to the server
            serverOutput.write(peerPort + "\n"); // Send peer's listener port
            serverOutput.flush();
            System.out.println("Server is aware of this clients peerListener for peer connections");

            // Handle initial server response
            String response = serverInput.readLine();
            if (response.startsWith("Connect to:")) {
                String[] parts = response.split(" ");
                String peerHost = parts[2];
                int peerPort = Integer.parseInt(parts[3]);
                connectToPeer(peerHost, peerPort); // Attempt to connect to the suggested peer
            } else {
                System.out.println(response); // "You are the first peer in the network."
            }
        } catch (IOException e) {
            isConnectedToServer = false;
            System.out.println("Failed to connect to the central server: " + e.getMessage());
        }
    }

    /**
     * Starts the peer's server to handle incoming peer connections.
     *
     * @param port The port number for the Peer's Server
     */
    private static void startPeerServer(int port) {
        try (ServerSocket peerServerSocket = new ServerSocket(port)) {
            System.out.println("Peer server is listening  for peer connections on port: " + BOLD_UNDERLINE + port + RESET_FORMATTING);

            while (true) {
                Socket clientSocket = peerServerSocket.accept();
                threadPool.submit(() -> peerListener(clientSocket)); // Handle connections concurrently
            }
        } catch (IOException e) {
            System.out.println("Error starting peer server: " + e.getMessage());
        }
    }

    /**
     * Listens for Peer connections requests and disconnects from peer upon receiving a disconnect request.
     *
     * @param peerSocket The peer socket for peer connections
     */
    private static void peerListener(Socket peerSocket) {
        InetSocketAddress remoteAddress = null;

        try {
            // Get the remote peer's address
            remoteAddress = new InetSocketAddress(peerSocket.getInetAddress(), peerSocket.getPort());

            // Handle the peer connection
            handlePeerConnection(peerSocket, remoteAddress);

        } catch (IOException e) {
            System.out.println("Error with peer " + remoteAddress + ": " + e.getMessage());
        } finally {
            // Cleanup the peer connection
            cleanupPeer(remoteAddress);
        }
    }

    /**
     * Attempts to establish a connection with the specified peer.
     * If the peer is already connected or the current peer has reached the maximum number of neighbors,
     * it redirects the connection request to another neighbor (if possible).
     *
     * @param host The host address of the target peer.
     * @param port The listening port of the target peer.
     */
    private static void connectToPeer(String host, int port) {
        InetSocketAddress peerAddress = new InetSocketAddress(host, port);

        // Prevent connecting to self
        if (peerPort == port && serverIPAddress.equals(host)) {
            System.out.println("Attempted to connect to self. Ignoring.");
            return;
        }

        // Ensure the peer is not already connected
        if (neighbors.containsKey(peerAddress)) {
            System.out.println("Already connected to peer: " + peerAddress.getHostName());
            return;
        }

        // Attempt to connect
        try {
            Socket peerSocket = new Socket(host, port);

            // Start a new thread to handle the peer connection
            threadPool.submit(() -> {
                try {
                    handlePeerConnection(peerSocket, peerAddress);
                } catch (IOException ignored) {

                }
            });

        } catch (IOException e) {
            System.out.println("Error connecting to peer: " + host + ":" + port);
        }
    }

    /**
     * Handles an incoming or established connection with a peer.
     * If the current peer has already reached the maximum number of neighbors,
     * it redirects the connection to another neighbor. Otherwise, it manages the
     * peer's communication and updates its own neighbors list.
     *
     * @param peerSocket   The socket representing the connection with the peer.
     * @param remoteAddress The address of the connecting peer.
     * @throws IOException If there is an error in communication with the peer.
     */
    private static void handlePeerConnection(Socket peerSocket, InetSocketAddress remoteAddress) throws IOException {
        // Check if already connected or max neighbors reached
        if (neighbors.size() >= 3) {
            redirectPeer(peerSocket, remoteAddress);
            return;
        }

        // Add the peer to the neighbors map
        if (neighbors.putIfAbsent(remoteAddress, peerSocket) != null) {
            System.out.println("Duplicate connection detected. Ignoring: " + remoteAddress);
            return;
        }

        System.out.println("Connected to peer: " + remoteAddress.getHostName());

        try (BufferedReader input = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()))) {

            // Send acknowledgment to the peer
            output.write("Accepted connection\n");
            output.flush();

            // Handle incoming messages
            String message;
            while ((message = input.readLine()) != null) {
                System.out.println("Message from " + remoteAddress + ": " + message);

                if (message.startsWith("REDIRECT")) {
                    String[] parts = message.split(" ");
                    if (parts.length == 3) {
                        String redirectHost = parts[1];
                        int redirectPort = Integer.parseInt(parts[2]);
                        System.out.println("Redirecting connection to: " + redirectHost + ":" + redirectPort);
                        cleanupPeer(remoteAddress);
                        connectToPeer(redirectHost, redirectPort);
                        break;
                    } else {
                        System.out.println("Malformed REDIRECT message from " + remoteAddress);
                    }
                }

                if ("disconnected!".equalsIgnoreCase(message.trim())) {
                    cleanupPeer(remoteAddress);
                    break;
                }
            }
        } catch (SocketException e) {
            if (e.getMessage().equals("Connection reset")) {
                System.out.println("Peer " + remoteAddress + " disconnected abruptly.");
            } else {
                throw e;
            }
        }
    }

    /**
     * Redirects a peer connection to a random neighbor when the maximum neighbor limit is reached.
     * Sends a "REDIRECT" message to the peer with the new target host and port, and cleans up the
     * connection for the redirected peer.
     *
     * @param peerSocket   The socket representing the connection with the peer to be redirected.
     * @param remoteAddress The address of the peer being redirected.
     */
    private static void redirectPeer(Socket peerSocket, InetSocketAddress remoteAddress) {
        try (BufferedWriter output = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()))) {
            InetSocketAddress redirectTarget = getRandomNeighbor();
            if (redirectTarget != null) {
                output.write("REDIRECT " + redirectTarget.getAddress().getHostAddress() + " " + redirectTarget.getPort() + "\n");
                output.flush();
                System.out.println("Redirected peer " + remoteAddress + " to " + redirectTarget);
            } else {
                System.out.println("No neighbors available for redirection.");
            }
        } catch (IOException e) {
            System.out.println("Error redirecting peer " + remoteAddress + ": " + e.getMessage());
        } finally {
            cleanupPeer(remoteAddress);
        }
    }

    private static void cleanupPeer(InetSocketAddress remoteAddress) {
        if (remoteAddress != null) {
            try {
                // Remove the peer from neighbors and close the socket
                Socket removedSocket = neighbors.remove(remoteAddress);
                if (removedSocket != null && !removedSocket.isClosed()) {
                    removedSocket.close();
                    System.out.println("Closed socket for peer: " + remoteAddress);
                }
            } catch (IOException e) {
                System.out.println("Failed to close socket for peer: " + remoteAddress + ". Error: " + e.getMessage());
            }
        }
    }

    /**
     * Disconnects from the central server.
     */
    private static void disconnectFromServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                isConnectedToServer = false;
                System.out.println("Disconnected from the central server.");
            }
        } catch (IOException e) {
            System.out.println("Error disconnecting from the server: " + e.getMessage());
        }
    }

    /**
     * Disconnects from all neighbors.
     */
    private static void disconnectFromNeighbors() {
        for (InetSocketAddress neighbor : neighbors.keySet()) {
            try (Socket neighborSocket = neighbors.remove(neighbor);
                 BufferedWriter neighborOutput = new BufferedWriter(new OutputStreamWriter(neighborSocket.getOutputStream()))) {

                neighborOutput.write("disconnected!\n");
                neighborOutput.flush();
                cleanupPeer(neighbor);
                System.out.println("Notified neighbor: " + neighbor.getHostName());
            } catch (IOException e) {
                System.out.println("Failed to notify neighbor: " + neighbor);
            }
        }
    }

    /**
     * Returns a random neighbor from the current list of neighbors.
     *
     * @return A random neighbor's InetSocketAddress or null if no neighbors are present
     */
    private static InetSocketAddress getRandomNeighbor() {
        if (neighbors.isEmpty()) {
            return null;
        }
        int randomIndex = new Random().nextInt(neighbors.size());
        return (InetSocketAddress) neighbors.keySet().toArray()[randomIndex];
    }

    /**
     * Displays the current neighbors of this peer.
     */
    private static void displayNeighbors() {
        if (neighbors.isEmpty()) {
            System.out.println("No neighbors connected.");
        } else {
            System.out.println("Your neighbors:");
            for (InetSocketAddress neighbor : neighbors.keySet()) {
                System.out.println("- " + neighbor.getHostName());
            }
        }
    }
}