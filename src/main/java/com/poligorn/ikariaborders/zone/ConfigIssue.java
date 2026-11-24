package com.poligorn.ikariaborders.zone;

/** A config problem with the path to the offending field, e.g. {@code zones[2].shape.half}. */
public record ConfigIssue(String path, String message) {
    @Override public String toString() {
        return path + ": " + message;
    }
}
