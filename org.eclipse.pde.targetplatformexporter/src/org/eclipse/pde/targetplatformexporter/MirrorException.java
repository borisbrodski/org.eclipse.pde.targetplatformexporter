package org.eclipse.pde.targetplatformexporter;

public class MirrorException extends Exception {
	private static final long serialVersionUID = 42L;

	public MirrorException() {
		super();
	}

	public MirrorException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public MirrorException(String message, Throwable cause) {
		super(message, cause);
	}

	public MirrorException(String message) {
		super(message);
	}

	public MirrorException(Throwable cause) {
		super(cause);
	}
}