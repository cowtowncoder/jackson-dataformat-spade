package manual;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.roaringbitmap.RoaringBitmap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.spade.BitmapEncoder;
import com.fasterxml.jackson.dataformat.spade.NibblerEncoder;

/**
 * Test tool to check how well bitset compression libs work.
 */
public class CompressBitSets
{
    private final ObjectMapper JSON_MAPPER = new ObjectMapper();
    {
        JSON_MAPPER.enable(JsonParser.Feature.ALLOW_COMMENTS);
    }

    private CompressBitSets() { }

    public static void main(String[] args) throws IOException
    {
        if (args.length < 1) {
            System.err.println("Usage: java ... "+CompressBitSets.class.getName()+" <input json file>");
            System.exit(1);
        }
        new CompressBitSets().run(args[0]);
    }

    protected void run(String filename) throws IOException
    {
        Bitsets bitsets = JSON_MAPPER.readValue(new File(filename), Bitsets.class);
        final int rows = bitsets.rowCount;
        System.out.printf("Read %d records, with %d columns\n", rows, bitsets.columnCount);

        Set<String> seenResults = new HashSet<>();

        boolean firstEmpty = true;
        for (Map.Entry<String,BitsetRecord> entry : bitsets.bitsets.entrySet()) {
            BitsetRecord r = entry.getValue();
            byte[] rawSet = r.presence;
            if (rawSet == null) {
                if (firstEmpty) {
                    firstEmpty = false;
                    BitSet bs = new BitSet();
                    bs.set(0, rows);
                    rawSet = bs.toByteArray();
                } else {
                    continue;
                }
            }

            // Let's reduce noise by only using unique results:
            if (!seenResults.add(sha1(rawSet))) {
                continue;
            }
            System.out.printf("Column '%s': ", entry.getKey());

            double pct = 100.0 * (double) r.set / (double) rows;
            String pctDesc = (pct < 1.0) ? String.format("%.1f%%(%db)", pct, r.set)
                    : String.format("%.2f%%", pct);
            System.out.printf("%s full ", pctDesc);

            System.out.printf("%s(raw), %s(lzf), %s(gzip), %s(bitrat), %s(nibbler), %s(roaring)",
                    _length(rawSet.length),
                    _length(compressedLengthLZF(rawSet)),
                    _length(compressedLengthGzip(rawSet)),
                    _length(ratCompress(rawSet)),
                    _length(nibblerCompress(rawSet)),
                    _length(roaringCompress(rawSet))
            );
            System.out.println();
        }
    }

    static MessageDigest _sha1;
    static {
        try {
            _sha1 = MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    static String sha1(byte[] data) {
        try {
            return new String(_sha1.digest(data), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static int compressedLengthLZF(byte[] data) {
        return com.ning.compress.lzf.LZFEncoder.encode(data).length;
    }

    static int compressedLengthGzip(byte[] data) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(10 + data.length>>2);
        try {
            OutputStream gzip = new com.ning.compress.gzip.OptimizedGZIPOutputStream(bytes);
            gzip.write(data);
            gzip.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bytes.size();
    }

    private static int ratCompress(byte[] data)
    {
        byte[] input = new byte[4096];
        byte[] output = new byte[4096 + 100];
        final BitmapEncoder enc = new BitmapEncoder();
        int i = 0;
        int left = data.length;
        int totalOutput = 0;

        for (; left >= 4096; i += 4096, left -= 4096) {
            System.arraycopy(data, i, input, 0, 4096);
            enc.encodeFullChunk(false, input, output, 0);
            totalOutput += enc.getOutputPtr() + ratOverheadPerChunk();
        }
        if (left > 0) {
            System.arraycopy(data, i, input, 0, left);
            enc.encodePartialChunk(false, input, left, output, 0);
            totalOutput += enc.getOutputPtr() + ratOverheadPerChunk();
        }
        return totalOutput;
    }

    private static int ratOverheadPerChunk() {
        // one byte for bit mask, 2-byte length indicator
        return 1 + 2;
    }
    
    private static int nibblerCompress(byte[] data)
    {
        final int CHUNK_LEN = NibblerEncoder.MAX_CHUNK_SIZE;
        byte[] input = new byte[CHUNK_LEN];
        byte[] output = new byte[NibblerEncoder.MAX_OUTPUT_BUFFER];
        final NibblerEncoder enc = new NibblerEncoder();
        int i = 0;
        int left = data.length;
        int totalOutput = 0;

        for (; left >= CHUNK_LEN; i += CHUNK_LEN, left -= CHUNK_LEN) {
            System.arraycopy(data, i, input, 0, CHUNK_LEN);
            int outBytes = enc.encode(input, 0, CHUNK_LEN, output, 0);
            totalOutput += outBytes;
        }
        if (left > 0) {
            System.arraycopy(data, i, input, 0, left);
            int outBytes = enc.encode(input, 0, left, output, 0);
            totalOutput += outBytes;
        }
        return totalOutput;
    }

    private static int roaringCompress(byte[] data)
    {
        RoaringBitmap r = new RoaringBitmap();
        int ix = 0;
        for (int i = 0, end = data.length; i < end; ++i) {
            int ch = data[i];
            for (int mask = 0x80; mask != 0; mask >>= 1) {
                if ((ch & mask) != 0) {
                    r.add(ix);
                }
                ++ix;
            }
        }
        r.runOptimize();
        return r.serializedSizeInBytes();
    }
    
    private static String _length(int length) {
        if (length < 2048) {
            return String.format("%db", length);
        }
        if (length < (100 * 1024)) {
            return String.format("%.2fkB", length/1024.0);
        }
        return String.format("%.1fkB", length/1024.0);
    }
    
    static class Bitsets {
        public int rowCount;
        public int columnCount;
        public Map<String,BitsetRecord> bitsets;
    }

    static class BitsetRecord {
        public int set; // count of set bits
        public byte[] presence;
    }
}
