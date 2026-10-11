package com.faforever.server.utils;

public interface NoThrowCloseable extends AutoCloseable {
    @Override
    void close();
}
