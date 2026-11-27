public class ErrorMapper {

    public int statusFor(String exceptionName) {
        if (exceptionName == null) {
            return 500;
        }
        switch (exceptionName) {
            case "IllegalArgumentException":
            case "MethodArgumentNotValidException":
                return 400;
            case "AuthenticationException":
                return 401;
            case "AccessDeniedException":
                return 403;
            case "ProductNotFoundException":
                return 404;
            case "DuplicateProductException":
                return 409;
            default:
                return 500;
        }
    }

    private String reason(int status) {
        switch (status) {
            case 400:
                return "Bad Request";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 409:
                return "Conflict";
            default:
                return "Internal Server Error";
        }
    }

    public String bodyFor(String exceptionName, String message) {
        int status = statusFor(exceptionName);
        String text;
        if (status == 500) {
            text = message == null || message.trim().isEmpty() ? "Unexpected error" : message;
        } else if (message == null || message.trim().isEmpty()) {
            text = reason(status);
        } else {
            text = message;
        }
        return status + " " + reason(status) + ": " + text;
    }
}
