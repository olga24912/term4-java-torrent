package ru.spbau.mit;

import java.util.Arrays;

public class ClientAddress {
    private static final int CNT_BYTE_IN_IP = 4;
    private static final int HASH_NUMBER = 31;
    private short port;
    private byte[] ip;

    public ClientAddress() {
        ip = new byte[CNT_BYTE_IN_IP];
    }

    public void setPort(short port) {
        this.port = port;
    }

    public void setIp(byte[] ip) {
        this.ip = ip;
    }

    public short getPort() {
        return port;

    }

    public byte[] getIp() {
        return ip;
    }

    @Override

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ClientAddress that = (ClientAddress) o;

        return port == that.port && Arrays.equals(ip, that.ip);
    }

    @Override
    public int hashCode() {
        int result = (int) port;
        result = HASH_NUMBER * result + Arrays.hashCode(ip);
        return result;
    }
}
