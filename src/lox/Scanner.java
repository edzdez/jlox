package lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static lox.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and",    AND);
        keywords.put("class",  CLASS);
        keywords.put("else",   ELSE);
        keywords.put("false",  FALSE);
        keywords.put("for",    FOR);
        keywords.put("fun",    FUN);
        keywords.put("if",     IF);
        keywords.put("nil",    NIL);
        keywords.put("or",     OR);
        keywords.put("print",  PRINT);
        keywords.put("return", RETURN);
        keywords.put("super",  SUPER);
        keywords.put("this",   THIS);
        keywords.put("true",   TRUE);
        keywords.put("var",    VAR);
        keywords.put("while",  WHILE);
    }

    // Fields to help keep track of where we are in the source code
    private int start = 0;    // offset that indexes into the string
    private int current = 0;  // offset that indexes into the string
    private int line = 1;     // tracks the line of current so we can produce tokens that know their location

    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        // Keeps adding tokens until we reach the end
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        // Add a final EOF token (not necessary but makes it cleaner)
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    // Helper function to tell us when we've consumed all characters
    private boolean isAtEnd() {
        return current >= source.length();
    }

    // Recognizing lexemes
    private void scanToken() {
        char c = advance();

        switch (c) {
            // handle single character lexemes
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*':
                if (match('/')) {
                    Lox.error(line, "No beginning for block comment.");
                } else {
                    addToken(STAR);
                }
                break;

            // handle possible two character lexemes
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;

            // handle / (because comments start with a / too)
            case '/':
                if (match('/')) {
                    // A comment goes until the end of the line
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else if (match('*')) {
                    // Handle C-style block comments
                    blockComment();
                } else {
                    addToken(SLASH);
                }
                break;

            // Skip other meaningless characters
            case ' ':
            case '\r':
            case '\t':
                // Ignore whitespace.
                break;

            case '\n':
                // Ignore newlines
                line++;
                break;

            // String literals
            case '"': string(); break;

            default:
                if (isDigit(c)) {
                    // Handle numbers
                    number();
                } else if(isAlpha(c)) {
                    // Handle identifiers
                    identifier();
                } else {
                    // Handle case when we see a character that isn't used by Lox
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    // Helper methods
    private void blockComment() {
        int origLine = line;
        while (!isAtEnd() && !(peek() == '*' && peekNext() == '/')) {
            if (peek() == '\n') line++;
            advance();

            if (isAtEnd()) {
                break;
            }

            if (peek() == '/' && peekNext() == '*') {
                blockComment();
            }
        }

        for (int i = 0; i < 2; ++i) {
            if (isAtEnd()) {
                Lox.error(origLine, "Unterminated block comment");
                break;
            }
            else advance();
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();

        // Check if identifier is reserved
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) type = IDENTIFIER;

        addToken(type);
    }

    private void number() {
        while (!isDigit(peek())) advance();

        // Handle decimals
        if (peek() == '.' && isDigit(peekNext())) {
            // Consume the '.'
            advance();

            while (isDigit(peek())) advance();
        }

        addToken(NUMBER,
                Double.parseDouble(source.substring(start, current)));
    }

    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // The closing '"'
        advance();

        // Trim the surrounding quotes
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    private boolean match(char expected) {
        if (isAtEnd()) return false;

        // it's like a conditional advance, where we only consume current
        // if it's what we're looking for
        if (source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    // Sort of like advance, but doesn't consume the character
    // This is called 'lookahead'
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    // We could have added a parameter to peek specifying how much to look ahead
    // However, that allows for arbitrarily far lookahead. Thus, making two methods
    // makes it clearer that we only want to have 1 or 2 character lookahead
    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        return source.charAt(current + 1);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
               (c >= 'A' && c <= 'Z') ||
               (c == '_');
    }

    private boolean isAlphaNumeric(char c) {
        return isDigit(c) || isAlpha(c);
    }

    // consumes the next char in the source file and returns it
    private char advance() {
        return source.charAt(current++);
    }

    // grabs the text of the current lexeme and creates a new token for it
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    // this lets us handle tokens with literal values
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }
}
