package dev.dual.runtime.handler;

enum OverflowPolicy {
    FAIL,
    PRESERVE_FULL,
    DEGRADE,
    DROP_OPTIONAL
}
