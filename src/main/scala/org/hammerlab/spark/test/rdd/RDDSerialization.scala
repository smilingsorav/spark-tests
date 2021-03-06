package org.hammerlab.spark.test.rdd

import org.apache.spark.rdd.{ GetFileSplit, RDD }
import org.hammerlab.paths.Path
import org.hammerlab.spark.test.suite.SparkSuite
import org.hammerlab.hadoop.splits.PartFileBasename

import scala.reflect.ClassTag

/**
 * Base-trait for tests that check round-trip correctness and on-disk sizes for a given RDD-serde implementation.
 */
trait RDDSerialization
  extends SparkSuite {

  // Subclasses implement serializing and deserializing an RDD.
  protected def serializeRDD[T: ClassTag](rdd: RDD[T], path: Path): RDD[T]
  protected def deserializeRDD[T: ClassTag](path: Path): RDD[T]

  protected def verifyRDDSerde[T: ClassTag](elems: Seq[T]): Unit =
    verifyFileSizesAndSerde(elems)

  /**
   * Make an rdd out of `elems`, write it to disk, verify the written partitions' sizes match `fileSizes`, read it
   * back in, verify the contents are the same.
   *
   * @param fileSizes if one integer is provided here, assume 4 partitions with that size. If two are provided, assume
   *                  4 partitions where the first's size matches the first integer, and the remaining three match the
   *                  second. Otherwise, the partitions' sizes and number must match `fileSizes`.
   */
  protected def verifyFileSizesAndSerde[T: ClassTag](elems: Seq[T],
                                                     fileSizes: Int*): Unit =
    verifyFileSizeListAndSerde[T](elems, fileSizes)

  protected def verifyFileSizeListAndSerde[T: ClassTag](elems: Seq[T],
                                                        origFileSizes: Seq[Int]): Unit = {

    // If one or two "file sizes" were provided, expand them out into an array of length 4.
    val fileSizes: Seq[Int] =
      if (origFileSizes.size == 1)
        Array.fill(4)(origFileSizes.head)
      else if (origFileSizes.size == 2)
        origFileSizes ++ Array(origFileSizes(1), origFileSizes(1))
      else
        origFileSizes

    val numPartitions = fileSizes.size

    val path = tmpPath()

    val rdd = sc.parallelize(elems, numPartitions)

    serializeRDD[T](rdd, path)

    val fileSizeMap =
      fileSizes
        .zipWithIndex
        .map {
          case (size, idx) ⇒
            PartFileBasename(idx) →
              size
        }
        .toMap

    path
      .list("part-*")
      .map(
        p ⇒
          p.basename → p.size
      )
      .toMap should be(fileSizeMap)

    val after = deserializeRDD[T](path)

    after.getNumPartitions should be(numPartitions)
    after
      .partitions
      .map(
        GetFileSplit(_).path
      ) should be(
      (0 until numPartitions)
        .map(
          i ⇒
            path / PartFileBasename(i)
        )
    )
    after.collect() should be(elems.toArray)
  }
}
