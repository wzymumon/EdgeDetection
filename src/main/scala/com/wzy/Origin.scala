package com.wzy

import java.text.SimpleDateFormat
import java.util.Date

import com.wzy.allocation.Allocation
import com.wzy.monitor.WorkerMonitor
import geotrellis.raster.Tile
import geotrellis.raster.mapalgebra.focal.Square
import geotrellis.raster.resample.Bilinear
import geotrellis.spark.io.hadoop.HadoopSparkContextMethodsWrapper
import geotrellis.spark.tiling.FloatingLayoutScheme
import geotrellis.spark.{SpatialKey, TileLayerMetadata, withTilerMethods}
import geotrellis.vector.ProjectedExtent
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{SparkConf, SparkContext}


object Origin {
  def main(args: Array[String]): Unit = {

    val inputfile: String = args(0)
    val multiple: Int = args(1).toInt

    val sparkconf =
      new SparkConf()
        //.setMaster("local[*]")
        .setAppName("Spark Origin Repartition Application")
        .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .set("spark.kryo.registrator", "geotrellis.spark.io.kryo.KryoRegistrator")
        .setIfMissing("spark.kryoserializer.buffer.max", "256m")
        .setIfMissing("spark.kryoserializer.buffer", "64m")
        .setIfMissing("spark.driver.maxResultSize", "4g")
        .set("hdfsBasePath", "hdfs://namenode:8020")
        .set("spark-master", "10.101.241.5")

    val sc = new SparkContext(sparkconf)

    sc.setLogLevel("ERROR")

    // 获取各个节点的计算能力信息
    println(sc.applicationId)
    val workers: Seq[Worker] = WorkerMonitor.getAllworkers(sc.applicationId, sparkconf.get("spark-master"))
    var clusterTotalCores = 0
    workers.foreach(x => {
      clusterTotalCores += x.totalCores
    })

    //TODO 对节点进行评估
    val effects = evaluation.EvaluationCenter.workersToEffects(workers)

    // HDFS 配置
    val hdfsBasePath: String = sparkconf.get("hdfsBasePath")
    val inputPath: String = hdfsBasePath + "/input/" + inputfile
    val outputPath: String = hdfsBasePath + "/output/wzy/" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())

    // LOCAL 配置
    //val inputPath = inputfile
    //val outputPath: String = "output/" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())

    // 对Tiff格式进行读取
    val inputRdd: RDD[(ProjectedExtent, Tile)] = {
      sc.hadoopGeoTiffRDD(inputPath)
    }

    // 获取tiff图像元信息
    val (_, rasterMetaData) =
      TileLayerMetadata.fromRDD(inputRdd, FloatingLayoutScheme(512))

    val initnumPartitions = inputRdd.getNumPartitions
    val tiled: RDD[(SpatialKey, Tile)] = {
      inputRdd
        .tileToLayout(rasterMetaData.cellType, rasterMetaData.layout, Bilinear)
        .repartition(initnumPartitions)
    }

    val tiledrdd: tiled.type = tiled.persist(StorageLevel.MEMORY_AND_DISK)

    //TODO 统计每个分区的大小
    import com.wzy.extend.RddImplicit._
    val buckets: Seq[Bucket] = tiledrdd.fetchBuckets

    //TODO 分区匹配算法
    val indexToPrefs: Map[Int, Seq[String]] = Allocation.allocate(buckets, effects) // Max_Min Fairness 算法
    // val indexToPrefs: Map[Int, Seq[String]] = AllocationCenter.distrbutionByWeight(buckets, workers) // 按权重进行随机分配
    indexToPrefs.foreach(println)

    tiledrdd.mapValues { tile =>
      tile.focalMax(Square(3))
    }

    // 任务1
    tiled.mapValues { tile =>
      tile.focalMax(Square(3))
    }

    val count = tiled.mapValues { tile =>
      tile.focalMax(Square(3))
    }.count()

    print(s"Origin Application END $count")
    sc.stop()
  }
}