package org.jusecase.jte.output;

import org.jusecase.jte.TemplateOutput;

import java.io.PrintWriter;

@SuppressWarnings("unused") // By api users
public class PrintWriterOutput implements TemplateOutput {
    private final PrintWriter writer;

    public PrintWriterOutput(PrintWriter writer) {
        this.writer = writer;
    }

    @Override
    public void writeSafeContent(String value) {
        writer.write(value);
    }
}