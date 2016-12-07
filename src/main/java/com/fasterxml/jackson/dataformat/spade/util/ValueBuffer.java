package com.fasterxml.jackson.dataformat.spade.util;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

/**
 * Simple container object used for constructing test data for bitset compression
 * comparison: from simple tabular (but heterogenous data -- think of a sequence
 * of shallow key/value hash maps) data calculate presence bitsets,
 * and allow serialization as well.
 */
public class ValueBuffer
    implements JsonSerializable
{
    public Map<String,Column> columns = new LinkedHashMap<>();

    public ValueBuffer() { }

    public void addValue(int row, String name, Object value)
    {
        Column col = columns.get(name);
        if (col == null) {
            col = new Column(name);
            columns.put(name, col);
        }
        col.append(row, value);
    }

    public int columnCount() { return columns.size(); }

    @Override
    public void serializeWithType(JsonGenerator gen, SerializerProvider provider, TypeSerializer ts) throws IOException {
        serialize(gen, provider);
    }

    @Override
    public void serialize(JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        for (Column col : columns.values()) {
            gen.writeFieldName(col.name);
            col.serialize(gen, provider);
        }
        gen.writeEndObject();
    }

    public static class Column implements JsonSerializable
    {
        public final String name;
        public final List<Object> values = new ArrayList<>();
        public final BitSet presence = new BitSet();
        public int lastRow = -1; // for sanity checking
        public int entries;

        public Column(String n) {
            name = n;
        }

        public void append(int rowNr, Object value) {
            if (rowNr <= lastRow) {
                throw new IllegalArgumentException(String.format(
                        "Bad row index %d for property '%s': last given was %s",
                        rowNr, name, lastRow));
            }
            lastRow = rowNr;
            values.add(value);
            presence.set(rowNr);
            ++entries;

// sanity check
//            if (values.size() != entries) throw new Error("Entries '"+entries+", but buffered "+values.size()+" values");
        }

        @Override
        public void serialize(JsonGenerator gen, SerializerProvider provider) throws IOException
        {
            gen.writeStartArray();
            // first: write presence set as byte[] (note: could optimize, won't for now)
            gen.writeBinary(presence.toByteArray());
            // then all values
            int row = 0;
            for (Object ob : values) {
                ++row;
                if (ob == null) {
                    gen.writeNull(); // should we ever get these?
                } else if (ob instanceof String) {
                    gen.writeString((String) ob);
                } else if (ob instanceof Boolean) {
                    gen.writeBoolean(((Boolean) ob).booleanValue());
                } else if (ob instanceof Number) {
                    if (ob instanceof Integer) {
                        gen.writeNumber((Integer) ob);
                    } else if (ob instanceof Long) {
                        gen.writeNumber((Long) ob);
                    } else if (ob instanceof Double) {
                        gen.writeNumber((Double) ob);
                    } else if (ob instanceof BigDecimal) {
                        gen.writeNumber((BigDecimal) ob);
                    } else {
                        throw new IllegalStateException("Weird number: "+ob.getClass());
                    }
                } else if (ob instanceof Character) {
                    gen.writeString(ob.toString());
                } else if (ob instanceof Collection<?>) {
                    Collection<?> values = (Collection<?>) ob;
                    gen.writeStartArray();
                    if (!values.isEmpty()) {
                        for (Object value : values) {
                            gen.writeString((String) value);
                        }
                    }
                    gen.writeEndArray();
                } else if (ob instanceof String[]) {
                    String[] values = (String[]) ob;
                    gen.writeStartArray();
                    for (String value : values) {
                        gen.writeString(value);
                    }
                    gen.writeEndArray();
                } else {
                    throw new IllegalStateException(String.format(
                            "Weird value for field '%s', row #%d: %s",
                            name, row, ob.getClass()));
                }
            }
            gen.writeEndArray();
        }

        @Override
        public void serializeWithType(JsonGenerator gen, SerializerProvider provider, TypeSerializer tser)
                throws IOException {
            // won't support polymorphic types here so:
            serialize(gen, provider);
        }
    }
}
