package com.cartnest.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ApiError(int status, String error, String message, String path, Map<String, String> fieldErrors,
                       LocalDateTime timestamp) {
}
