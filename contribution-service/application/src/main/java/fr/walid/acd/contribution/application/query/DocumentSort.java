package fr.walid.acd.contribution.application.query;

import java.util.Objects;

public record DocumentSort(Field field, Direction direction) {
    public enum Field { TITLE, UPDATED_AT, STATUS, VERSION }
    public enum Direction { ASC, DESC }

    public DocumentSort {
        Objects.requireNonNull(field, "Sort field is required");
        Objects.requireNonNull(direction, "Sort direction is required");
    }
}
