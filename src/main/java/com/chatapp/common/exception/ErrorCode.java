package com.chatapp.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Room not found"),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Message not found"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "Username already exists"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "Email already exists"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired token"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid username or password"),
    ROOM_NOT_MEMBER(HttpStatus.FORBIDDEN, "You are not a member of this room"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "Already a member of this room"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please try again later"),
    CANNOT_MESSAGE_SELF(HttpStatus.BAD_REQUEST, "Cannot send a message to yourself");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
