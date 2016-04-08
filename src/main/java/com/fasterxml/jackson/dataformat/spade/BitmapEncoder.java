package com.fasterxml.jackson.dataformat.spade;

public class BitmapEncoder
{
    private final static byte ZERO_BYTE = 0;

    /**
     * Buffer from which input to encode is read.
     */
    protected final byte[] _input;

    protected final byte[] _output;

    int _inputPtr;
    
    // Pointer to point after last byte actually output
    int _outputTail;

    // 8-bit value that constitutes continuation of the match
    int _matchLevel1 = 0x0; // starts with clear bits
    
    public BitmapEncoder(byte[] input, int bitLength)
    {
        _input = input;
        // any trailing bogus bits to fix?
        int rem = (bitLength & 0x7);
        if (rem > 0) {
            _fixLast(input, (bitLength>>3), rem);
        }
        // Max overhead at level 1 is 1/32; at level 2 it's 1/64; and at level 3 1/512.
        // So let's round up to 1/16 for convenience
        int maxNeeded = input.length; 
        maxNeeded +=  ((maxNeeded + 15) >> 4) + 4;
        _output = new byte[maxNeeded];
    }

    /**
     * Top-level compressor that calls {@link #_encodeLevel2} to handle 8 of 512-byte chunks,
     * resulting in 4k input blocks, with 8-bit mask to indicate which chunks contain
     * literal bytes.
     */
    int _encodeLevel3(int outputPtr)
    {
        // Let's do this unrolled:
        int resultMask = _encodeLevel2(outputPtr+1);
        if (resultMask != 0) { // had output, so prepend mask
            _output[outputPtr] = (byte) resultMask;
            resultMask |= 0x80;
            outputPtr = _outputTail;
        }

        // and then 7 more times
        int mask = _encodeLevel2(outputPtr+1);
        if (mask != 0) {
            _output[outputPtr] = (byte) mask;
            resultMask |= 0x40;
            outputPtr = _outputTail;
        }
        mask = _encodeLevel2(outputPtr+1);
        if (mask != 0) {
            _output[outputPtr] = (byte) mask;
            resultMask |= 0x20;
            outputPtr = _outputTail;
        }
        mask = _encodeLevel2(outputPtr+1);
        if (mask != 0) {
            _output[outputPtr] = (byte) mask;
            resultMask |= 0x10;
            outputPtr = _outputTail;
        }
        mask = _encodeLevel2(outputPtr+1);
        if (mask != 0) {
            _output[outputPtr] = (byte) mask;
            resultMask |= 0x08;
            outputPtr = _outputTail;
        }
        mask = _encodeLevel2(outputPtr+1);
        if (mask != 0) {
            _output[outputPtr] = (byte) mask;
            resultMask |= 0x04;
            outputPtr = _outputTail;
        }
        mask = _encodeLevel2(outputPtr+1);
        if (mask != 0) {
            _output[outputPtr] = (byte) mask;
            resultMask |= 0x02;
            outputPtr = _outputTail;
        }
        mask = _encodeLevel2(outputPtr+1);
        if (mask != 0) {
            _output[outputPtr] = (byte) mask;
            resultMask |= 0x01;
            outputPtr = _outputTail;
        }
        return resultMask;
    }
    
    /**
     * Second-level encoding function that delegates to {@link #_encodeFullLevel1}
     * for 32 byte chunks, calling it 8 x 2 times, for total block size of
     * 512 bytes, with up to 16 marker bytes. Returns 8 bit mask to indicate chunks
     * that are NOT empty.
     *
     * @return 8-bit mask of blocks produced
     */
    int _encodeLevel2(int outputPtr)
    {
        int resultMask = 0;

        // Need 8 loops of 8 bytes each, with each level1-call getting a nibble
        int rounds = 8;
        // First: leave room for one-byte byte marker
        while (true) {
            final int origOutputPtr = outputPtr;
            ++outputPtr;
            int mask = _encodeFullLevel1(outputPtr);
            if (mask != 0) { // not a full run, appended output
                mask <<= 4;
                outputPtr = _outputTail;
            }
            int lo = _encodeFullLevel1(outputPtr);
            if (lo != 0) {
                outputPtr = _outputTail;
                mask |= lo;
            }
            if (mask == 0) { // no output, reset position
                outputPtr = origOutputPtr;
            } else { // had output, so prepend mask
                _output[origOutputPtr] = (byte) mask;
                resultMask |= 1;
            }
            if (--rounds == 0) {
                break;
            }
            resultMask <<= 1;
        }
        return resultMask;
    }
    
    /**
     * Lowest-level encoding method for full blocks: handles 32 bytes, that is, 256 bits.
     * Contains one special optimization for "non-compressing" content. Note that
     * contents of return value will be written as prefix bytes, as necessary (zeroes
     * omitted).
     *
     * @return 4-bit mask to indicate which of potential 8-byte blocks are included (that is,
     *    have further presence bit and 1-8 bytes of underlying data).
     */
    int _encodeFullLevel1(int outputPtr)
    {
        int match = _matchLevel1;
        int resultBits = 0;
        int origOutputPtr = outputPtr; // to check whether compression achieved
        int inputPtr = _inputPtr;

        /*
System.err.print(" encodeLevel1 #"+inputPtr+": ");        
for (int i = 0; i < 32; ++i) {
    System.err.printf(" %02x", _input[_inputPtr+i] & 0xFF);
}
System.err.println();
*/
        
        // Need 4 loops of 8 bytes each as prefixes are interleaved
        int rounds = 4;
        while (true) {
            final int baseOut = outputPtr;
            int mask = 0; // lowest-level mask for group of 8 bytes

            byte b = _input[inputPtr++];
            // Basic component, repeated 8 times: see if run continues; if not, output byte, add bit
            if ((b & 0xFF) != match) {
                _output[++outputPtr] = b; // important: advance first, to leave room for prefix
                match = ((b & 0x1) == 0) ? 0 : 0xFF;
                mask |= 0x80;
            }
            // and then repeat 7 more times
            b = _input[inputPtr++];
            if ((b & 0xFF) != match) {
                _output[++outputPtr] = b;
                match = ((b & 0x1) == 0) ? 0 : 0xFF;
                mask |= 0x40;
            }
            b = _input[inputPtr++];
            if ((b & 0xFF) != match) {
                _output[++outputPtr] = b;
                match = ((b & 0x1) == 0) ? 0 : 0xFF;
                mask |= 0x20;
            }
            b = _input[inputPtr++];
            if ((b & 0xFF) != match) {
                _output[++outputPtr] = b;
                match = ((b & 0x1) == 0) ? 0 : 0xFF;
                mask |= 0x10;
            }
            b = _input[inputPtr++];
            if ((b & 0xFF) != match) {
                _output[++outputPtr] = b;
                match = ((b & 0x1) == 0) ? 0 : 0xFF;
                mask |= 0x08;
            }
            b = _input[inputPtr++];
            if ((b & 0xFF) != match) {
                _output[++outputPtr] = b;
                match = ((b & 0x1) == 0) ? 0 : 0xFF;
                mask |= 0x04;
            }
            b = _input[inputPtr++];
            if ((b & 0xFF) != match) {
                _output[++outputPtr] = b;
                match = ((b & 0x1) == 0) ? 0 : 0xFF;
                mask |= 0x02;
            }
            b = _input[inputPtr++];
            if ((b & 0xFF) != match) {
                _output[++outputPtr] = b;
                match = ((b & 0x1) == 0) ? 0 : 0xFF;
                mask |= 0x01;
            }

            // and then assess the situation: did we output anything?
            if (mask != 0) { // yup: add prefix mask
                _output[baseOut] = (byte) mask;
                ++outputPtr; // since it pointed to the last added byte
                resultBits |= 1;
            }
            if (--rounds == 0) {
                break;
            }
            resultBits <<= 1;
        }

        // Three main outcomes:
        //
        // (a) completely compressed out; nothing output, zero returned
        // (b) enough compression, left as is
        // (c) not enough compression; rewrite with single 0 byte, copy 32 literal bytes after
        //    (which is safe as zero bytes are never otherwise encoded, "parent-bit" above indicates zero/non-zero)
        
        // and then update settings unless we had full run
        if (resultBits != 0) { // (b) or (c)
            int amount = outputPtr - origOutputPtr;

            if (amount > 32) { // (c), need to re-do
                _output[origOutputPtr++] = ZERO_BYTE;
                System.arraycopy(_input, _inputPtr, _output, origOutputPtr, 32);
                outputPtr = origOutputPtr+32;
                // also ensure we declare everything to be non-compressed
                resultBits = 0xF;
            }
            // at lowest level we are sure to advance the pointer
            _outputTail = outputPtr;
            _matchLevel1 = match;
        }
        _inputPtr = inputPtr;
        return resultBits;
    }

    /*
    /**********************************************************************
    /* Internal helper methods
    /**********************************************************************
     */
    
    // Helper method for changing extra unused bits to be the same
    // as the last actual content bit; this to make sure last run
    // is not accidentally broken by garbage
    final static void _fixLast(byte[] input, int offset, int lastBits)
    {
        int old = input[offset];
        int shift = (8 - lastBits);
        boolean lastSet = ((old >> shift) & 1) != 0;
        int mask = 0xFF >> lastBits;
        int changed;
        if (lastSet) {
            changed = old | mask;
        } else {
            changed = old & ~mask;
        }
        if (changed != old) {
            input[offset] = (byte) changed;
        }
    }
}
