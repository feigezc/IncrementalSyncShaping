package com.alibaba.middleware.race.sync.server2;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import static com.alibaba.middleware.race.sync.Constants.*;

/**
 * Created by yche on 6/18/17.
 * used for scan the byte arr of record string lines
 */
public class RecordScanner {
    // input
    private final ByteBuffer mappedByteBuffer;
    private final int endIndex;   // exclusive

    // intermediate states
    private final ByteBuffer tmpBuffer = ByteBuffer.allocate(64);
    private final ByteBuffer fieldNameBuffer = ByteBuffer.allocate(128);
    private int nextIndex; // start from startIndex

    // output
    private final ByteBuffer retByteBuffer; // fast-consumption object
    private final ArrayList<RecordKeyValuePair> recordWrapperArrayList; // fast-consumption object

    public RecordScanner(ByteBuffer mappedByteBuffer, int startIndex, int endIndex,
                         ByteBuffer retByteBuffer, ArrayList<RecordKeyValuePair> retRecordWrapperArrayList) {
        this.mappedByteBuffer = mappedByteBuffer.asReadOnlyBuffer(); // get a view, with local position, limit
        this.nextIndex = startIndex;
        this.endIndex = endIndex;
        this.retByteBuffer = retByteBuffer;
        this.recordWrapperArrayList = retRecordWrapperArrayList;
    }

    // stop at `|`
    private void skipField() {
        if (mappedByteBuffer.get(nextIndex) == FILED_SPLITTER) {
            nextIndex++;
        }
        while (mappedByteBuffer.get(nextIndex) != FILED_SPLITTER) {
            nextIndex++;
        }
    }

    private void putIntoByteBufferUntilFieldSplitter() {
        if (mappedByteBuffer.get(nextIndex) == FILED_SPLITTER) {
            nextIndex++;
        }
        byte myByte;
        while ((myByte = mappedByteBuffer.get(nextIndex)) != FILED_SPLITTER) {
            retByteBuffer.put(myByte);
            nextIndex++;
        }
        // add `\n`
        retByteBuffer.put(LINE_SPLITTER);
    }

    private long getNextLong() {
        tmpBuffer.clear();
        if (mappedByteBuffer.get(nextIndex) == FILED_SPLITTER)
            nextIndex++;

        byte tmpByte;
        while ((tmpByte = mappedByteBuffer.get(nextIndex)) != FILED_SPLITTER) {
            nextIndex++;
            tmpBuffer.put(tmpByte);
        }
        tmpBuffer.flip();

        return Long.valueOf(new String(tmpBuffer.array(), 0, tmpBuffer.limit()));
    }

    private void skipFieldName() {
        // skip '|'
        nextIndex++;
        // stop at '|'
        byte myByte;
        fieldNameBuffer.clear();
        while ((myByte = mappedByteBuffer.get(nextIndex)) != FILED_SPLITTER) {
            fieldNameBuffer.put(myByte);
            nextIndex++;
        }
        fieldNameBuffer.flip();
    }

    private RecordKeyValuePair scanOneRecord() {
        // 1st: skip: mysql, ts, schema, table
        for (int i = 0; i < 4; i++) {
            skipField();
        }

        // 2nd: parse KeyOperation
        byte operation = mappedByteBuffer.get(nextIndex + 1);
        // skip one splitter and operation byte
        nextIndex += 2;
        KeyOperation keyOperation = new KeyOperation(operation);
        skipField();
        if (operation == I_OPERATION) {
            // insert: pre(null) -> cur
            skipField();
            keyOperation.curKey(getNextLong());
        } else if (operation == D_OPERATION) {
            // delete: pre -> cur(null)
            keyOperation.preKey(getNextLong());
            skipField();
        } else {
            // update
            keyOperation.preKey(getNextLong());
            keyOperation.curKey(getNextLong());
        }

        // 3rd: parse ValueIndex
        ValueIndexArrWrapper valueIndexArrWrapper = null;
        while (mappedByteBuffer.get(nextIndex + 1) != LINE_SPLITTER) {
            if (valueIndexArrWrapper == null) {
                valueIndexArrWrapper = new ValueIndexArrWrapper();
            }

            skipFieldName();
            long curOffset = retByteBuffer.position();
            skipField();
            putIntoByteBufferUntilFieldSplitter();
            long nextOffset = retByteBuffer.position();
            valueIndexArrWrapper.addIndex(fieldNameBuffer, curOffset, (short) (nextOffset - curOffset));
        }
        // skip '|' and `\n`
        nextIndex += 2;
        return new RecordKeyValuePair(keyOperation, valueIndexArrWrapper);
    }

    public void compute() {
        while (nextIndex < endIndex) {
            recordWrapperArrayList.add(scanOneRecord());
        }
    }
}
