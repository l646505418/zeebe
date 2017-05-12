/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.hashindex;

import static org.agrona.BitUtil.SIZE_OF_BYTE;
import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.hashindex.store.FileChannelIndexStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class Long2BytesHashIndexTest
{
    protected static final int INDEX_SIZE = 15;
    protected static final int VALUE_LENGTH = 3 * SIZE_OF_BYTE;

    private static final byte[] VALUE = "bar".getBytes();
    private static final byte[] ANOTHER_VALUE = "plo".getBytes();

    private Long2BytesHashIndex index;
    private FileChannelIndexStore indexStore;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void createIndex()
    {
        indexStore = FileChannelIndexStore.tempFileIndexStore();
        index = newIndex();
        assertThat(index.indexSize).isEqualTo(16);
    }

    protected Long2BytesHashIndex newIndex()
    {
        return new Long2BytesHashIndex(indexStore, INDEX_SIZE, 1, VALUE_LENGTH);
    }

    @After
    public void close()
    {
        indexStore.close();
    }


    @Test
    public void shouldReturnNullForEmptyMap()
    {
        // given that the map is empty
        assertThat(index.get(0)).isNull();
    }

    @Test
    public void shouldReturnNullForNonExistingKey()
    {
        // given
        index.put(1, VALUE);

        // then
        assertThat(index.get(0)).isNull();
    }

    @Test
    public void shouldReturnLongValueForKey()
    {
        // given
        index.put(1, VALUE);

        // if then
        assertThat(index.get(1)).isEqualTo(VALUE);
    }

    @Test
    public void shouldRemoveValueForKey()
    {
        // given
        index.put(1, VALUE);

        // if
        final byte[] removeResult = index.remove(1);

        //then
        assertThat(removeResult).isEqualTo(VALUE);
        assertThat(index.get(1)).isNull();
    }

    @Test
    public void shouldNotRemoveValueForDifferentKey()
    {
        // given
        index.put(1, VALUE);
        index.put(2, ANOTHER_VALUE);

        // if
        final byte[] removeResult = index.remove(1);

        //then
        assertThat(removeResult).isEqualTo(VALUE);
        assertThat(index.get(1)).isNull();
        assertThat(index.get(2)).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldNotRemoveValueForNonExistingKey()
    {
        // given
        index.put(1, VALUE);

        // if
        final byte[] removeResult = index.remove(0);

        //then
        assertThat(removeResult).isNull();
        assertThat(index.get(1)).isEqualTo(VALUE);
    }

    @Test
    public void shouldSplit()
    {
        // given
        index.put(0, VALUE);

        // if
        index.put(1, ANOTHER_VALUE);

        // then
        assertThat(index.blockCount()).isEqualTo(2);
        assertThat(index.get(0)).isEqualTo(VALUE);
        assertThat(index.get(1)).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldSplitTwoTimes()
    {
        // given
        index.put(1, VALUE);
        assertThat(index.blockCount()).isEqualTo(1);

        // if
        index.put(3, ANOTHER_VALUE);

        // then
        assertThat(index.blockCount()).isEqualTo(3);
        assertThat(index.get(1)).isEqualTo(VALUE);
        assertThat(index.get(3)).isEqualTo(ANOTHER_VALUE);
    }

    @Test
    public void shouldPutMultipleValues()
    {
        for (int i = 0; i < 16; i += 2)
        {
            index.put(i, VALUE);
        }

        for (int i = 1; i < 16; i += 2)
        {
            index.put(i, ANOTHER_VALUE);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(i)).isEqualTo(i % 2 == 0 ? VALUE : ANOTHER_VALUE);
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void shouldPutMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(i, i < 8 ? VALUE : ANOTHER_VALUE);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.get(i)).isEqualTo(i < 8 ? VALUE : ANOTHER_VALUE);
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void shouldReplaceMultipleValuesInOrder()
    {
        for (int i = 0; i < 16; i++)
        {
            index.put(i, VALUE);
        }

        for (int i = 0; i < 16; i++)
        {
            assertThat(index.put(i, ANOTHER_VALUE)).isTrue();
        }

        assertThat(index.blockCount()).isEqualTo(16);
    }

    @Test
    public void cannotPutValueIfIndexFull()
    {
        // given
        index.put(0, VALUE);

        thrown.expect(RuntimeException.class);

        index.put(16, ANOTHER_VALUE);
    }

    @Test
    public void cannotPutValueIfValueIsTooLong()
    {
        thrown.expect(IllegalArgumentException.class);

        index.put(0, "too long".getBytes());
    }

    @Test
    public void shouldClear()
    {
        // given
        index.put(0, VALUE);

        // when
        index.clear();

        // then
        assertThat(index.get(0)).isNull();
    }

    @Test
    @Ignore("https://github.com/camunda-tngp/util/issues/15")
    public void shouldAccessIndexWithNewObject()
    {
        // given
        index.put(0, VALUE);
        index.flush();
        indexStore.flush();

        // a second index working on the same index store (e.g. after broker restart)
        final Long2BytesHashIndex newIndex = new Long2BytesHashIndex(indexStore);
        newIndex.reInit();

        // when
        final byte[] returnValue = newIndex.get(0);

        // then
        assertThat(returnValue).containsExactly(VALUE);
    }

    @Test
    @Ignore("https://github.com/camunda-tngp/util/issues/16")
    public void shouldNotOverwriteValue()
    {
        // given
        final byte[] originalAnotherValue = ANOTHER_VALUE.clone();
        index.put(0, VALUE);
        index.put(1, ANOTHER_VALUE);

        // when
        index.get(0);

        // then
        assertThat(ANOTHER_VALUE).containsExactly(originalAnotherValue);
    }

}
