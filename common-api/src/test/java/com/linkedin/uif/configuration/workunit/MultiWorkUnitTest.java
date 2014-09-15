package com.linkedin.uif.configuration.workunit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.io.Closer;

import com.linkedin.uif.source.workunit.MultiWorkUnit;
import com.linkedin.uif.source.workunit.WorkUnit;

/**
 * Unit tests for {@link MultiWorkUnit}.
 */
@Test(groups = {"com.linkedin.uif.configuration.workunit"})
public class MultiWorkUnitTest {

  private MultiWorkUnit multiWorkUnit;

  @BeforeClass
  public void setUp() {
    this.multiWorkUnit = new MultiWorkUnit();

    WorkUnit workUnit1 = new WorkUnit();
    workUnit1.setHighWaterMark(1000);
    workUnit1.setLowWaterMark(0);
    workUnit1.setProp("k1", "v1");
    this.multiWorkUnit.addWorkUnit(workUnit1);

    WorkUnit workUnit2 = new WorkUnit();
    workUnit2.setHighWaterMark(2000);
    workUnit2.setLowWaterMark(1001);
    workUnit2.setProp("k2", "v2");
    this.multiWorkUnit.addWorkUnit(workUnit2);
  }

  @Test
  public void testSerDe() throws IOException {
    Closer closer = Closer.create();
    try {
      ByteArrayOutputStream baos = closer.register(new ByteArrayOutputStream());
      DataOutputStream dos = closer.register(new DataOutputStream(baos));
      this.multiWorkUnit.write(dos);

      ByteArrayInputStream bais = closer.register((new ByteArrayInputStream(baos.toByteArray())));
      DataInputStream dis = closer.register((new DataInputStream(bais)));
      MultiWorkUnit copy = new MultiWorkUnit();
      copy.readFields(dis);

      List<WorkUnit> workUnitList = copy.getWorkUnits();
      Assert.assertEquals(workUnitList.size(), 2);

      Assert.assertEquals(workUnitList.get(0).getHighWaterMark(), 1000);
      Assert.assertEquals(workUnitList.get(0).getLowWaterMark(), 0);
      Assert.assertEquals(workUnitList.get(0).getProp("k1"), "v1");

      Assert.assertEquals(workUnitList.get(1).getHighWaterMark(), 2000);
      Assert.assertEquals(workUnitList.get(1).getLowWaterMark(), 1001);
      Assert.assertEquals(workUnitList.get(1).getProp("k2"), "v2");
    } finally {
      closer.close();
    }
  }
}
