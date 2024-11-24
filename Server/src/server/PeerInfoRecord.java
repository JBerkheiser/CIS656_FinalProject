package server;

import java.net.InetAddress;

public record PeerInfoRecord(InetAddress address, int outgoingPort, int peerListenerPort) {

    @Override
    public String toString() {
        return address.getHostAddress() + ":" + peerListenerPort + " (Outgoing port: " + outgoingPort + ")";
    }
}
