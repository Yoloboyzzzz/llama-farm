package be.ucll.slicer.cli;

public class PrusaSlicerException extends RuntimeException {

    public PrusaSlicerException(String message) {
        super(message);
    }

    public PrusaSlicerException(String message, Throwable cause) {
        super(message, cause);
    }
}
