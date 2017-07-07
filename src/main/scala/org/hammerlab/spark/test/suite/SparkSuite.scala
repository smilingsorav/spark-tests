package org.hammerlab.spark.test.suite

import org.hammerlab.test.Suite

/**
 * Base for test suites that shar one [[org.apache.spark.SparkContext]] across all test cases.
 */
trait SparkSuite
  extends Suite
    with SparkSuiteBase {

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    makeSparkContext
  }

  override def afterAll(): Unit = {
    super.afterAll()
    clear()
  }
}
