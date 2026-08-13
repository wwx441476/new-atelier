package com.example.atelier.document.extract;

import java.util.concurrent.atomic.AtomicInteger;

public final class BlockIds {

    private final AtomicInteger seq = new AtomicInteger(0);
    private final String prefix;

    public BlockIds(String prefix) {
        this.prefix = prefix == null ? "b" : prefix;
    }

    public String next() {
        return prefix + "-" + seq.incrementAndGet();
    }
}
