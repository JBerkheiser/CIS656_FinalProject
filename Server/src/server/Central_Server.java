package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Central_Server {
    static int serverPort = 9090;
    private static final ConcurrentMap<InetSocketAddress, PeerInfoRecord> connectedPeers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
            System.out.println("The Server is on!");
            System.out.println("See server-logs.txt for logs");

            // Start a thread to listen for terminal commands
            new Thread(() -> handleServerCommands(serverSocket)).start();

            // Main server loop to handle peer connections
            while (!serverSocket.isClosed()) {
                try {
                    Socket PeerSocket = serverSocket.accept();
                    new Thread(new PeerHandler(PeerSocket)).start();
                } catch (IOException e) {
                    if (serverSocket.isClosed()) {
                        break; // Exit the loop when the server socket is closed
                    } else {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if ("Socket closed".equalsIgnoreCase(e.getMessage())) {
                System.out.println("Server socket closed. Shutting down...");
            } else {
                e.printStackTrace();
            }
        }
    }

    /**
     * Notifies all connected peers with a specific message.
     *
     * @param message The message to send.
     */
    public static void notifyAllPeers(String message) {
        for (PeerInfoRecord peerInfo : connectedPeers.values()) {
            try (Socket peerSocket = new Socket(peerInfo.address(), peerInfo.peerListenerPort());
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream()))) {

                // Send the notification message
                writer.write(message + "\n");
                writer.flush();
                System.out.println("Notified peer: " + peerInfo);

            } catch (IOException e) {
                System.err.println("Failed to notify peer: " + peerInfo + ". Error: " + e.getMessage());
            }
        }
    }

    /**
     * Handles terminal commands <b>server side</b> (members/quit).
     *
     * @param serverSocket The main server socket.
     */
    private static void handleServerCommands(ServerSocket serverSocket) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("Enter command (members/quit): ");
                String command = scanner.nextLine().trim();

                if (command.equalsIgnoreCase("members")) {
                    // List all connected peers
                    if (connectedPeers.isEmpty()) {
                        System.out.println("No peers are currently connected.");
                    } else {
                        System.out.println("Connected peers:");
                        for (InetSocketAddress peer : connectedPeers.keySet()) {
                            System.out.println("- " + peer);
                        }
                    }
                } else if (command.equalsIgnoreCase("quit")) {
                    notifyAllPeers("Server Shutdown");
                    // Shut down the server
                    System.out.println("Shutting down the server...");
                    serverSocket.close(); // Stop accepting new connections
                    System.exit(0); // Exit the program
                } else {
                    System.out.println("Unknown command. Available commands: members, quit.");
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling server commands: " + e.getMessage());
        }
    }

    /**
     * Adds a peer to the list of connected peers.
     *
     * @param peerSocket       The socket used to connect to the server (provides outgoing port).
     * @param peerListenerPort The port the peer is listening on for connections from other peers.
     */
    public static synchronized void addPeer(Socket peerSocket, int peerListenerPort) {
        InetAddress address = peerSocket.getInetAddress();
        int outgoingPort = peerSocket.getPort(); // The port used by the peer to connect to the server

        // Create a PeerInfoRecord to store both ports
        PeerInfoRecord peerInfo = new PeerInfoRecord(address, outgoingPort, peerListenerPort);

        // Use the outgoing port to index connectedPeers
        InetSocketAddress peerAddress = new InetSocketAddress(address, outgoingPort);

        // Add to the connectedPeers map
        PeerInfoRecord existingPeer = connectedPeers.put(peerAddress, peerInfo);
        if (existingPeer != null) {
            System.out.println("Replaced existing peer: " + existingPeer + " with: " + peerInfo);
        } else {
            System.out.println("Peer added: " + peerInfo);
        }
    }

    /**
     * Removes a peer from the connectedPeers map.
     *
     * @param peerAddress The address of the peer to remove.
     */
    public static synchronized void removePeer(InetSocketAddress peerAddress) {
        PeerInfoRecord removedPeer = connectedPeers.remove(peerAddress);
        if (removedPeer != null) {
            System.out.println("\nPeer removed: " + removedPeer);
        } else {
            System.out.println("Peer not found in the network: " + peerAddress);
        }
    }


    /**
     * Returns a random peer's PeerInfoRecord from the list of connected peers, excluding a specific peer.
     *
     * @param excludingPeer The peer to exclude from the random selection.
     * @return A PeerInfoRecord of a random peer, or null if no other peers are available.
     */
    public static synchronized PeerInfoRecord getRandomPeer(InetSocketAddress excludingPeer) {
        // Filter out the requesting peer based on the outgoing port
        List<PeerInfoRecord> availablePeers = connectedPeers.entrySet().stream()
                .filter(entry -> {
                    InetSocketAddress key = entry.getKey();
                    PeerInfoRecord value = entry.getValue();

                    // Exclude the peer if its outgoing port matches the requesting peer's outgoing port
                    return !(key.getPort() == excludingPeer.getPort() &&
                            key.getAddress().equals(excludingPeer.getAddress()));
                })
                .map(Map.Entry::getValue) // Extract the PeerInfoRecord values
                .toList();

        // Return null if no other peers are available
        if (availablePeers.isEmpty()) {
            return null;
        }

        // Randomly select a peer
        Random random = new Random();
        return availablePeers.get(random.nextInt(availablePeers.size()));
    }
}