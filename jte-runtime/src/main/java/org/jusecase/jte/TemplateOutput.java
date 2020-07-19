package org.jusecase.jte;

import java.io.Writer;

@SuppressWarnings("unused") // Methods are called by generated templates
public interface TemplateOutput {
    Writer getWriter();

    void writeContent(String value);

    default void writeContent(Enum<?> value) {
        if (value != null) {
            writeContent(value.toString());
        }
    }

    default void writeContent(boolean value) {
        writeContent(String.valueOf(value));
    }

    default void writeContent(byte value) {
        writeContent(String.valueOf(value));
    }

    default void writeContent(short value) {
        writeContent(String.valueOf(value));
    }

    default void writeContent(int value) {
        writeContent(String.valueOf(value));
    }

    default void writeContent(long value) {
        writeContent(String.valueOf(value));
    }

    default void writeContent(float value) {
        writeContent(String.valueOf(value));
    }

    default void writeContent(double value) {
        writeContent(String.valueOf(value));
    }

    default void writeContent(char value) {
        writeContent(String.valueOf(value));
    }
}