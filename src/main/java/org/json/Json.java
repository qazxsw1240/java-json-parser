package org.json;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class Json {
    private static final @NotNull String WHITESPACES = " \t\r\n";

    private static int skipWhitespaces(int @NotNull [] chars, int offset) {
        int code;
        while (offset < chars.length) {
            code = chars[offset];
            if (WHITESPACES.indexOf(code) < 0)
                break;
            offset++;
        }
        return offset;
    }

    public static int getDecimal(int hex, int offset) throws JsonSyntaxException {
        if ('0' <= hex && hex <= '9') {
            return hex - '0';
        } else if ('a' <= hex && hex <= 'f') {
            return hex - 'a' + 10;
        } else if ('A' <= hex && hex <= 'F') {
            return hex - 'A' + 10;
        } else {
            throw new JsonSyntaxException(hex, offset);
        }
    }

    @NotNull
    public static String stringify(@Nullable Object jsonData) {
        return stringify(jsonData, 0);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public static String stringify(@Nullable Object jsonData, int depth) {
        if (jsonData == null)
            return "null";
        if (jsonData instanceof BigDecimal || jsonData instanceof Boolean)
            return jsonData.toString();
        if (jsonData instanceof String)
            return "\"" + jsonData + "\"";
        if (jsonData instanceof List) {
            List<Object> array = (List<Object>) jsonData;
            if (array.isEmpty())
                return "[]";
            Iterator<Object> iterator = array.iterator();
            StringBuilder builder = new StringBuilder().append('[');
            while (iterator.hasNext()) {
                builder
                    .append('\n')
                    .append("  ".repeat(depth + 1))
                    .append(stringify(iterator.next(), depth + 1));
                if (iterator.hasNext())
                    builder.append(',');
            }
            return builder
                .append('\n')
                .append("  ".repeat(depth))
                .append(']')
                .toString();
        }
        if (jsonData instanceof Map) {
            Map<String, Object> object = (Map<String, Object>) jsonData;
            if (object.isEmpty())
                return "{}";
            Iterator<Map.Entry<String, Object>> iterator = object.entrySet().iterator();
            StringBuilder builder = new StringBuilder().append('{');
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                String key = entry.getKey();
                Object value = entry.getValue();
                builder
                    .append('\n')
                    .append("  ".repeat(depth + 1))
                    .append(stringify(key, depth + 1))
                    .append(" : ")
                    .append(stringify(value, depth + 1));
                if (iterator.hasNext())
                    builder.append(',');
            }
            return builder
                .append('\n')
                .append("  ".repeat(depth))
                .append('}')
                .toString();
        }
        throw new JsonSyntaxException("Invalid Object to stringify as JSON object");
    }

    @Nullable
    public static Object parse(@NotNull String content) throws JsonSyntaxException {
        int[] chars = content.chars().toArray();
        try {
            int offset = skipWhitespaces(chars, 0);
            JsonParserFrame frame = parseValue(chars, offset);
            offset = skipWhitespaces(chars, frame.nextOffset);
            if (offset != chars.length)
                throw new JsonSyntaxException(chars[offset], offset);
            return frame.value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new JsonSyntaxException("Unexpected end of JSON input");
        }
    }

    @NotNull
    private static JsonParserFrame parseValue(
        int @NotNull [] chars,
        int offset
    ) throws JsonSyntaxException {
        int ch = chars[offset = skipWhitespaces(chars, offset)];
        switch (ch) {
            case '{':
                return parseObject(chars, offset);
            case '[':
                return parseArray(chars, offset);
            case '"':
                return parseString(chars, offset);
            case '-':
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
                return parseNumber(chars, offset);
            case 't':
            case 'f':
                return parseBoolean(chars, offset);
            case 'n':
                return parseNull(chars, offset);
            default:
                throw new JsonSyntaxException(chars[offset], offset);
        }
    }

    @NotNull
    public static JsonParserFrame parseObject(
        int @NotNull [] chars,
        int offset
    ) throws ArrayIndexOutOfBoundsException, JsonSyntaxException {
        if (chars[offset] != '{') {
            throw new JsonSyntaxException(chars[offset], offset);
        } else {
            offset++;
        }
        int length = chars.length;
        LinkedHashMap<String, Object> object = new LinkedHashMap<>();
        JsonParserFrame frame;
        while (offset != length && chars[offset = skipWhitespaces(chars, offset)] != '}') {
            String key;
            Object value;
            frame = parseString(chars, offset);
            key = (String) frame.value;
            offset = skipWhitespaces(chars, frame.nextOffset);
            if (chars[offset] != ':')
                throw new JsonSyntaxException(chars[offset], offset);
            frame = parseValue(chars, skipWhitespaces(chars, offset + 1));
            value = frame.value;
            offset = skipWhitespaces(chars, frame.nextOffset);
            if (chars[offset] != ',' && chars[offset] != '}') {
                throw new JsonSyntaxException(chars[offset], offset);
            } else {
                object.put(key, value);
                if (chars[offset] == ',')
                    offset++;
            }
        }
        if (chars[offset] != '}')
            throw new JsonSyntaxException(chars[offset], offset);
        return new JsonParserFrame(offset + 1, object);
    }

    @NotNull
    public static JsonParserFrame parseArray(
        int @NotNull [] chars,
        int offset
    ) throws ArrayIndexOutOfBoundsException, JsonSyntaxException {
        if (chars[offset] != '[') {
            throw new JsonSyntaxException(chars[offset], offset);
        } else {
            offset++;
        }
        int length = chars.length;
        List<Object> array = new ArrayList<>();
        JsonParserFrame frame;
        while (offset != length && chars[offset = skipWhitespaces(chars, offset)] != ']') {
            Object value;
            frame = parseValue(chars, offset);
            value = frame.value;
            offset = skipWhitespaces(chars, frame.nextOffset);
            if (chars[offset] != ',' && chars[offset] != ']') {
                throw new JsonSyntaxException(chars[offset], offset);
            } else {
                array.add(value);
                if (chars[offset] == ',')
                    offset++;
            }
        }
        if (chars[offset] != ']')
            throw new JsonSyntaxException(chars[offset], offset);
        return new JsonParserFrame(offset + 1, array);
    }

    @NotNull
    public static JsonParserFrame parseString(
        int @NotNull [] chars,
        int offset
    ) throws ArrayIndexOutOfBoundsException, JsonSyntaxException {
        if (chars[offset] != '"') {
            throw new JsonSyntaxException(chars[offset], offset);
        } else {
            offset++;
        }
        int length = chars.length;
        boolean offsetSkipped;
        boolean escape = false;
        StringBuilder buffer = new StringBuilder();
        while (offset < length && chars[offset] != '"') {
            offsetSkipped = false;
            int ch = chars[offset];
            if (ch < 0x0020 || ch > 0x10FFFF)
                throw new JsonSyntaxException(ch, offset);
            if (escape) {
                switch (ch) {
                    case '"': {
                        buffer.append('"');
                        break;
                    }
                    case '\\': {
                        buffer.append('\\');
                        break;
                    }
                    case '/': {
                        buffer.append('/');
                        break;
                    }
                    case 'b': {
                        buffer.append('\b');
                        break;
                    }
                    case 'f': {
                        buffer.append('\f');
                        break;
                    }
                    case 'n': {
                        buffer.append('\n');
                        break;
                    }
                    case 'r': {
                        buffer.append('\r');
                        break;
                    }
                    case 't': {
                        buffer.append('\t');
                        break;
                    }
                    case 'u': {
                        offsetSkipped = true;
                        int codePoint = getDecimal(chars[offset + 1], offset + 1);
                        codePoint = codePoint << 4 | getDecimal(chars[offset + 2], offset + 2);
                        codePoint = codePoint << 4 | getDecimal(chars[offset + 3], offset + 3);
                        codePoint = codePoint << 4 | getDecimal(chars[offset + 4], offset + 4);
                        buffer.appendCodePoint(codePoint);
                        offset += 5;
                        break;
                    }
                    default:
                        throw new JsonSyntaxException(ch, offset);
                }
                escape = false;
            } else {
                if (ch == '\\') {
                    escape = true;
                } else {
                    buffer.appendCodePoint(ch);
                }
            }
            if (!offsetSkipped) offset++;
        }
        if (chars[offset] != '"')
            throw new JsonSyntaxException(chars[offset], offset);
        return new JsonParserFrame(offset + 1, buffer.toString());
    }

    @NotNull
    public static JsonParserFrame parseNumber(
        int @NotNull [] chars,
        int offset
    ) throws ArrayIndexOutOfBoundsException, JsonSyntaxException {
        int length = chars.length;
        StringBuilder buffer = new StringBuilder();
        int codePoint;
        if ((codePoint = chars[offset]) == '-') {
            buffer.appendCodePoint(codePoint);
            offset++;
        }
        if ((codePoint = chars[offset]) == '0') {
            buffer.appendCodePoint(codePoint);
            offset++;
        } else if ('0' <= codePoint && codePoint <= '9') {
            buffer.appendCodePoint(codePoint);
            offset++;
        } else {
            throw new JsonSyntaxException(codePoint, offset);
        }
        while (offset < length && ('0' <= (codePoint = chars[offset]) && codePoint <= '9')) {
            buffer.appendCodePoint(codePoint);
            offset++;
        }
        int scale;
        if (offset < length && (codePoint = chars[offset]) == '.') { // fraction check
            buffer.appendCodePoint(codePoint);
            offset++;
            scale = 0;
            while (offset < length && ('0' <= (codePoint = chars[offset]) && codePoint <= '9')) {
                buffer.appendCodePoint(codePoint);
                offset++;
                scale++;
            }
            if (scale == 0)
                throw new JsonSyntaxException(codePoint, offset);
        }
        if (offset < length &&
            ((codePoint = chars[offset]) == 'e' || codePoint == 'E')
        ) { // exponent check
            buffer.appendCodePoint(codePoint);
            offset++;
            if (offset < length &&
                ((codePoint = chars[offset]) == '+' || codePoint == '-')) { // sign check
                buffer.appendCodePoint(codePoint);
                offset++;
            }
            scale = 0;
            while (offset < length && ('0' <= (codePoint = chars[offset]) && codePoint <= '9')) {
                buffer.appendCodePoint(codePoint);
                offset++;
                scale++;
            }
            if (scale == 0)
                throw new JsonSyntaxException(codePoint, offset);
        }
        return new JsonParserFrame(offset, new BigDecimal(buffer.toString()));
    }

    @NotNull
    public static JsonParserFrame parseBoolean(
        int @NotNull [] chars,
        int offset
    ) throws ArrayIndexOutOfBoundsException, JsonSyntaxException {
        if (chars[offset] == 't' &&
            chars[offset + 1] == 'r' &&
            chars[offset + 2] == 'u' &&
            chars[offset + 3] == 'e'
        ) {
            return new JsonParserFrame(offset + 4, true);
        } else if (chars[offset] == 'f' &&
            chars[offset + 1] == 'a' &&
            chars[offset + 2] == 'l' &&
            chars[offset + 3] == 's' &&
            chars[offset + 4] == 'e'
        ) {
            return new JsonParserFrame(offset + 5, false);
        } else {
            throw new JsonSyntaxException(chars[offset], offset);
        }
    }

    @NotNull
    public static JsonParserFrame parseNull(
        int @NotNull [] chars,
        int offset
    ) throws ArrayIndexOutOfBoundsException, JsonSyntaxException {
        if (chars[offset] == 'n' &&
            chars[offset + 1] == 'u' &&
            chars[offset + 2] == 'l' &&
            chars[offset + 3] == 'l'
        ) {
            return new JsonParserFrame(offset + 4, null);
        } else {
            throw new JsonSyntaxException(chars[offset], offset);
        }
    }

    // TEST
    public static void main(@NotNull String[] args) {
        URI uri = URI.create("https://jsonplaceholder.typicode.com/todos/");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(uri)
            .build();
        String content = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(HttpResponse::body)
            .join();
        Object value = parse(content);
        System.out.println(stringify(value));
    }

    private static class JsonParserFrame {
        public final int nextOffset;
        public final @Nullable Object value;

        public JsonParserFrame(int offset, @Nullable Object value) {
            this.nextOffset = offset;
            this.value = value;
        }
    }
}
