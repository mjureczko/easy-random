package org.jeasy.random;

import org.jeasy.random.randomizers.range.IntegerRangeRandomizer;

public class SizeDrivenAbstractPopulator {
    protected final IntegerRangeRandomizer integerRangeRandomizer;

    public SizeDrivenAbstractPopulator(EasyRandomParameters parameters) {
        EasyRandomParameters.Range<Integer> collectionSizeRange = parameters.getCollectionSizeRange();
        integerRangeRandomizer = new IntegerRangeRandomizer(collectionSizeRange.getMin(), collectionSizeRange.getMax(), parameters.getSeed());
    }

    protected int getRandomSize() {
        return integerRangeRandomizer.getRandomValue();
    }
}
