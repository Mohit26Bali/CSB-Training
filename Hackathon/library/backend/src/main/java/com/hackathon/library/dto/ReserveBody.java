package com.hackathon.library.dto;

import jakarta.validation.constraints.NotNull;

public record ReserveBody(@NotNull(message = "Choose a book") Long bookId) {
}
