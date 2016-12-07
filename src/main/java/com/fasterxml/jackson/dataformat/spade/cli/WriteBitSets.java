package com.fasterxml.jackson.dataformat.spade.cli;

import java.io.*;
import java.util.Map;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.spade.util.ValueBuffer;

/**
 * Helper tool for writing out "raw" bitsets for presence information over
 * sample applog file.
 */
public class WriteBitSets
{
    private final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private WriteBitSets() { }

    public static void main(String[] args) throws IOException
    {
        if (args.length < 1) {
            System.err.println("Usage: java ... "+WriteBitSets.class.getName()+" <input file>");
            System.exit(1);
        }
        new WriteBitSets().run(args[0]);
    }

    protected void run(String inputPath) throws IOException
    {
        File file = new File(inputPath);
        if (!file.exists()) {
            System.err.printf("File '%s' does not exist.\n", file.getPath());
            System.exit(1);
        }
        String filename = file.getName();
        try (InputStream in = new FileInputStream(file)) {
            process(in, filename);
        }
    }

    protected void process(InputStream in, String filename) throws IOException
    {
        System.err.printf("Starting to read '%s'...\n", filename);

        // For columnar storage, need plenty of buffering
        final ValueBuffer valueBuffer = new ValueBuffer();
        int row = 0;

        try (MappingIterator<Map<String,Object>> it = JSON_MAPPER
                .readerFor(Map.class)
                .readValues(in))
        {
            while (it.hasNextValue()) {
                Map<String,Object> value = it.nextValue();
                for (Map.Entry<String,Object> entry : value.entrySet()) {
                    valueBuffer.addValue(row, entry.getKey(), entry.getValue());
                }
                ++row;
            }
        }

        
        System.err.printf("Read %d lines, found %d columns. Will now output bitset output...\n",
                row, valueBuffer.columnCount());
        JsonGenerator gen = JSON_MAPPER.getFactory().createGenerator(System.out);
        gen.useDefaultPrettyPrinter();
        gen.writeStartObject();
        gen.writeNumberField("rowCount", row);
        gen.writeNumberField("columnCount", valueBuffer.columnCount());
        gen.writeFieldName("bitsets");
        gen.writeStartObject();
        for (ValueBuffer.Column col : valueBuffer.columns.values()) {
            gen.writeFieldName(col.name);
            gen.writeStartObject();
            gen.writeNumberField("set", col.entries);
            // minor simplification: if "all set", write as number 1
            if (col.entries == row) { // write as if there was a property+value
                gen.writeRaw("\n      /* presence: 100% */");
            } else {
                gen.writeFieldName("presence");
                gen.writeBinary(col.presence.toByteArray());
            }
            gen.writeEndObject();
        }
        gen.writeEndObject();
        gen.writeEndObject();
        gen.close();
        System.err.printf("All done!\n");
    }
}
