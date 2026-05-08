package net.opanel.utils;

public class AnvilUtility {
    public static int[] decodeBitpacked(long[] packed, int bitsPerValue) {
        final int valueAmount = 64 / bitsPerValue;
        final long mask = (1L << bitsPerValue) - 1;

        int[] values = new int[valueAmount * packed.length];
        for(int i = 0; i < packed.length; i++) {
            long packedLong = packed[i];
            for(int j = 0; j < valueAmount; j++) {
                values[i * valueAmount + j] = (int) (packedLong & mask);
                packedLong >>>= bitsPerValue;
            }
        }
        return values;
    }
}
