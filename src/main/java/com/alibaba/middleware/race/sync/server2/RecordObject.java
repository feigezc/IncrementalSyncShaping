package com.alibaba.middleware.race.sync.server2;

import static com.alibaba.middleware.race.sync.server2.PipelinedComputation.propertyValueFetcher;

/**
 * Created by yche on 6/18/17.
 */
class RecordObject {
    final long key;
    private final ValueIndexArrWrapper valueIndexArrWrapper;

    RecordObject(long key, ValueIndexArrWrapper valueIndexArrWrapper) {
        this.key = key;
        this.valueIndexArrWrapper = valueIndexArrWrapper;
    }

    String getOneLine() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(key).append('\t');
        for (int i = 0; i < RecordField.FILED_NUM; i++) {
            IndexPair indexPair = valueIndexArrWrapper.get(i);
            stringBuilder.append(propertyValueFetcher.fetchProperty(indexPair.offset, indexPair.length)).append('\t');
        }
        stringBuilder.setLength(stringBuilder.length() - 1);
        return stringBuilder.toString();
    }
}