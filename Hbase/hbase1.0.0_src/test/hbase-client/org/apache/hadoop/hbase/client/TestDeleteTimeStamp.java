/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
 * law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 */

package org.apache.hadoop.hbase.client;

import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableMap;

import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.testclassification.SmallTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(SmallTests.class)
public class TestDeleteTimeStamp {
  private static final byte[] ROW = Bytes.toBytes("testRow");
  private static final byte[] FAMILY = Bytes.toBytes("testFamily");
  private static final byte[] QUALIFIER = Bytes.toBytes("testQualifier");

  /*
   * Test for verifying that the timestamp in delete object is being honored.
   * @throws Exception
   */
  @Test
  public void testTimeStamp() {
    long ts = 2014L;
    Delete delete = new Delete(ROW);
    delete.setTimestamp(ts);
    delete.deleteColumn(FAMILY, QUALIFIER);
    NavigableMap<byte[], List<Cell>> familyCellmap = delete.getFamilyCellMap();
    for (Entry<byte[], List<Cell>> entry : familyCellmap.entrySet()) {
      for (Cell cell : entry.getValue()) {
        Assert.assertEquals(ts, cell.getTimestamp());
      }
    }
  }
}
