package org.camunda.tngp.log;

import static uk.co.real_logic.agrona.BitUtil.*;

import java.nio.ByteBuffer;

import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

import static org.assertj.core.api.Assertions.*;
import static org.camunda.tngp.hashindex.HashIndexDescriptor.*;

public class Bytes2LongHashIndexLargeBlockSizeTest
{
    static long MISSING_VALUE = -2;

    byte[][] keys = new byte[16][64];

    Bytes2LongHashIndex index;

    UnsafeBuffer blockBuffer;
    UnsafeBuffer indexBuffer;

    @Before
    public void createIndex()
    {
        int indexSize = 32;
        int blockLength = BLOCK_DATA_OFFSET  + 3 * framedRecordLength(64, SIZE_OF_LONG);

        indexBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(requiredIndexBufferSize(indexSize)));
        blockBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(requiredBlockBufferSize(indexSize, blockLength)));

        index = new Bytes2LongHashIndex(indexBuffer, blockBuffer, indexSize, blockLength, 64);

        // generate keys
        for (int i = 0; i < keys.length; i++)
        {
            final byte[] val = String.valueOf(i).getBytes();

            for (int j = 0; j < val.length; j++)
            {
                keys[i][j] = val[j];
            }
        }
    }

    @Test
    public void shouldReturnMissingValueForEmptyMap()
    {
       // given that the map is empty
       assertThat(index.get(keys[0], MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnMissingValueForNonExistingKey()
    {
       // given
       index.put(keys[1], 1);

       // then
       assertThat(index.get(keys[0], MISSING_VALUE) == MISSING_VALUE);
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        index.put(keys[1], 1);

        // if then
        assertThat(index.get(keys[1], MISSING_VALUE)).isEqualTo(1);
    }

    @Test
    public void shouldNotSplit()
    {
        // given
        index.put(keys[0], 0);

        // if
        index.put(keys[1], 1);

        // then
        assertThat(index.blockCount()).isEqualTo(1);
        assertThat(index.get(keys[0], MISSING_VALUE)).isEqualTo(0);
        assertThat(index.get(keys[1], MISSING_VALUE)).isEqualTo(1);
    }


    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            index.put(keys[i], i);
        }

        for (int i = 1; i < 16; i += 2)
        {
            index.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(keys[i], MISSING_VALUE) == i);
        }
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(keys[i], MISSING_VALUE) == i);
        }
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(keys[i], i);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.put(keys[i], i)).isTrue();
        }

        assertThat(index.blockCount()).isEqualTo(6);
    }

}