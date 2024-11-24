package server;

import java.net.InetAddress;
import java.util.Objects;

public class PeerInfoRecord {
    private final InetAddress address;
    private final int outgoingPort;
    private final int peerListenerPort;

    public PeerInfoRecord(InetAddress address, int outgoingPort, int peerListenerPort) {
        this.address = address;
        this.outgoingPort = outgoingPort;
        this.peerListenerPort = peerListenerPort;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getOutgoingPort() {
        return outgoingPort;
    }

    public int getPeerListenerPort() {
        return peerListenerPort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfoRecord that = (PeerInfoRecord) o;
        return outgoingPort == that.outgoingPort &&
                peerListenerPort == that.peerListenerPort &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, outgoingPort, peerListenerPort);
    }

    @Override
    public String toString() {
        return address.getHostAddress() + ":" + peerListenerPort + " (Outgoing port: " + outgoingPort + ")";
    }
}