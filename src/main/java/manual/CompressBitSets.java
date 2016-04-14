package manual;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.*;

import org.roaringbitmap.RoaringBitmap;

import com.fasterxml.jackson.dataformat.spade.BitmapEncoder;
import com.fasterxml.jackson.dataformat.spade.NibblerEncoder;

/**
 * Test tool to check how well bitset compression libs work.
 */
public class CompressBitSets
    extends ToolBase
{
    private CompressBitSets() { }

    public static void main(String[] args) throws IOException
    {
        if (args.length < 1) {
            System.err.println("Usage: java ... "+CompressBitSets.class.getName()+" <input json file>");
            System.exit(1);
        }
        new CompressBitSets().run(args[0]);
    }

    protected Bitsets readBitsets(String filename) throws IOException
    {
        Bitsets bs = JSON_MAPPER.readValue(new File(filename), Bitsets.class);
        final int rows = bs.rowCount;
        System.out.printf("Read %d records, with %d columns\n", rows, bs.columnCount);

        Set<String> seenResults = new HashSet<>();

        boolean firstEmpty = true;

        Iterator<Map.Entry<String,BitsetRecord>> it = bs.bitsets.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<String,BitsetRecord> entry = it.next();
            BitsetRecord r = entry.getValue();
            byte[] rawSet = r.presence;
            if (rawSet == null) {
                if (firstEmpty) {
                    firstEmpty = false;
                    BitSet full = new BitSet();
                    full.set(0, rows);
                    rawSet = full.toByteArray();
                    r.presence = rawSet;
                } else {
                    it.remove();
                }
            } else {
                // Let's reduce noise by only using unique results:
                if (!seenResults.add(sha1(rawSet))) {
                    it.remove();
                }
            }
        }
        System.out.printf("... of which %d unique.\n", bs.bitsets.size());
        return bs;
    }
    
    protected void run(String filename) throws IOException
    {
        Bitsets bitsets = readBitsets(filename);
        int rows = bitsets.rowCount;
        for (Map.Entry<String,BitsetRecord> entry : bitsets.bitsets.entrySet()) {
            BitsetRecord r = entry.getValue();

            double pct = 100.0 * (double) r.set / (double) rows;
            String pctDesc = (pct < 1.0) ? String.format("%.1f%%(%db)", pct, r.set)
                    : String.format("%.2f%%", pct);
            System.out.printf("Column '%s' (%s): ", entry.getKey(), pctDesc);
            byte[] rawSet = r.presence;
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

    static int roaringCompress(byte[] data)
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
}
