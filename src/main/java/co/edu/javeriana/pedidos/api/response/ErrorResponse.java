package co.edu.javeriana.pedidos.api.response;

public class ErrorResponse {

    private String code;
    private String message;
    private String timestamp;

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message, String timestamp) {
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }
}