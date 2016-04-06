package com.fasterxml.jackson.dataformat.spade;

public class BitmapEncoder
{
    protected final byte[] _input;

    protected final byte[] _output;

    public BitmapEncoder(byte[] input, int bitLength)
    {
        _input = input;
        // any trailing bogus bits to fix?
        int rem = (bitLength & 0x7);
        if (rem > 0) {
            _fixLast(input, (bitLength>>3), rem);
        }
        // Should need at most 1/8 + 1/64 + 1/512 extra, round up to 3/16
        int maxNeeded = input.length; 
        maxNeeded +=  ((maxNeeded + 7) >> 3) + ((maxNeeded + 15) >> 4);
        _output = new byte[maxNeeded];
    }

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

    int _inputPtr;
    
    // Pointer to point after last byte actually output
    int _outputTail;

    // 8-bit value that constitutes continuation of the match
    int _matchLevel1 = 0x0; // starts with clear bits

    // Lowest-level encoding method for full blocks: handles 32 bytes, that is, 256 bits
    int _encodeFullLevel1(int outputPtr)
    {
        int match = _matchLevel1;
        int result = 0;

        for (int i = 0; i < 32; ++i) {
            byte b = _input[_inputPtr++];
            result <<= 1;
            // If run continues, nothing to output
            if ((b & 0xFF) == match) {
                continue;
            }
            // but if run ended, need to output byte, indicate non-empty
            _output[outputPtr++] = b;
            if ((b & 0x1) == 0) { // empty last bit?
                match = 0;
            } else { // last bit was set
                match = 0xFF;
            }
            result |= 1;
        }

        // and then update settings unless we had full run
        if (result != 0) {
            // at lowest level we are sure to advance the pointer
            _outputTail = outputPtr;
            _matchLevel1 = match;
        }
        return result;
    }
}
