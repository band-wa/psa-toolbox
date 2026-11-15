package android.car;

public class CarNotConnectedException extends Exception {
    public CarNotConnectedException() {
        super();
    }

    public CarNotConnectedException(String name) {
        super(name);
    }

    public CarNotConnectedException(String name, Throwable cause) {
        super(name, cause);
    }

    public CarNotConnectedException(Exception cause) {
        super(cause);
    }
}
