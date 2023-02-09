package org.json;

import org.jetbrains.annotations.NotNull;

public class JsonSyntaxException extends RuntimeException {
    private static final long serialVersionUID = 4282853659499275913L;

    JsonSyntaxException(@NotNull String message) {
        super(message);
    }

    JsonSyntaxException(int ch, int position) {
        super("Unexpected token " + new StringBuilder().appendCodePoint(ch) + " in JSON at position " + position);
    }
}
