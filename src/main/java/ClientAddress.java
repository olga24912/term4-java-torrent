import java.util.Arrays;

public class ClientAddress {
    short port;
    byte[] ip;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ClientAddress that = (ClientAddress) o;

        return port == that.port && Arrays.equals(ip, that.ip);

    }

    @Override
    public int hashCode() {
        int result = (int) port;
        result = 31 * result + Arrays.hashCode(ip);
        return result;
    }
}