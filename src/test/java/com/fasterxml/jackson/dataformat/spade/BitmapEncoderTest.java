package com.fasterxml.jackson.dataformat.spade;

import java.util.Arrays;

public class BitmapEncoderTest extends ModuleTestBase
{
    public void testFixLast()
    {
        // with 4 bits in last byte, last bit is 1, so should fill
        _verifyFixLast(0xF0, 4, 0xFF);
    }
    
    private void _verifyFixLast(int base, int lastBits, int exp)
    {
        byte[] b = new byte[] { (byte) base };
        BitmapEncoder._fixLast(b, 0, lastBits);
        assertEquals(exp, b[0] & 0xFF);

        // also verify it works with offset too
        b = new byte[3];
        b[1] = (byte) base;
        BitmapEncoder._fixLast(b, 1, lastBits);
        assertEquals(exp, b[1] & 0xFF);
        // and that no change occurs for adjacent values
        assertEquals(0, b[0]);
        assertEquals(0, b[2]);
    }

    public void testFullLevel1()
    {
        byte[] input = new byte[32];
        BitmapEncoder encoder = new BitmapEncoder(input, input.length * 8);
        assertEquals(0, encoder._inputPtr);
        assertEquals(0, encoder._outputTail);
        assertEquals(0, encoder._matchLevel1);

        // with empty contents should just get straight run
        
        int result = encoder._encodeFullLevel1(0);
        assertEquals(0, result);
        // but has now consumed input...
        assertEquals(32, encoder._inputPtr);
        assertEquals(0, encoder._outputTail);
        assertEquals(0, encoder._matchLevel1);

        // with all 1s, bit different due to assumption of starting with '0'
        input = new byte[32];
        Arrays.fill(input, (byte) 0xFF);
        encoder = new BitmapEncoder(input, input.length * 8);
        result = encoder._encodeFullLevel1(0);
        assertEquals(0x80000000, result);
        assertEquals(32, encoder._inputPtr);
        assertEquals(1, encoder._outputTail);
        assertEquals(0xFF, encoder._output[0] & 0xFF);
        assertEquals(0xFF, encoder._matchLevel1);

        // and more, with 16 0s, 16s, yet different
        input = new byte[32];
        Arrays.fill(input, 16, 32, (byte) 0xFF);
        encoder = new BitmapEncoder(input, input.length * 8);
        result = encoder._encodeFullLevel1(0);
        assertEquals(0x8000, result);
        assertEquals(32, encoder._inputPtr);
        assertEquals(1, encoder._outputTail);
        assertEquals(0xFF, encoder._output[0] & 0xFF);
        assertEquals(0xFF, encoder._matchLevel1);

        // and finally with zigzag pattern
        input = new byte[32];
        Arrays.fill(input, (byte) 0xAA);
        encoder = new BitmapEncoder(input, input.length * 8);
        result = encoder._encodeFullLevel1(0);
        assertEquals(-1, result);
        assertEquals(32, encoder._inputPtr);
        assertEquals(32, encoder._outputTail);
        // the very last bit is 0 so
        assertEquals(0x00, encoder._matchLevel1);

        // and just to ensure bit is properly checked
        input = new byte[32];
        Arrays.fill(input, (byte) 0x55);
        encoder = new BitmapEncoder(input, input.length * 8);
        result = encoder._encodeFullLevel1(0);
        assertEquals(-1, result);
        assertEquals(32, encoder._inputPtr);
        assertEquals(32, encoder._outputTail);
        // the very last bit is now 1:
        assertEquals(0xFF, encoder._matchLevel1);
    }
}
