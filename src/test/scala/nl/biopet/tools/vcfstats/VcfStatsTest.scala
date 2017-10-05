package nl.biopet.tools.vcfstats

import nl.biopet.test.BiopetTest
import org.testng.annotations.Test

class VcfStatsTest extends BiopetTest {
  @Test
  def testNoArgs(): Unit = {
    intercept[IllegalArgumentException] {
      VcfStats.main(Array())
    }
  }
}
