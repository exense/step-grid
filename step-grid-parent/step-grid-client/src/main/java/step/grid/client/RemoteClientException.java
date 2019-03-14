package step.grid.client;

public class RemoteClientException extends RuntimeException {

	public RemoteClientException(String message, Throwable cause) {
		super(message, cause);
	}

	public RemoteClientException(String message) {
		super(message);
	}

	public RemoteClientException(Throwable cause) {
		super(cause);
	}

}
