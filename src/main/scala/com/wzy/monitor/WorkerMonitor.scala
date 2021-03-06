package com.wzy.monitor

import scalaj.http.Http
import upickle.default.ReadWriter
import com.wzy._
import ujson.Value.Value

/**
 * 节点性能监控模块
 * 采集作业执行相关的节点监控信息
 */
object WorkerMonitor {

  private var workers: Seq[Worker] = _

  /**
   *
   * @param applicationId 提交的任务ID
   * @param master        spark监控的入口
   * @return
   */
  def getAllworkers(applicationId: String, master: String): Seq[Worker] = {
    val response = Http(s"http://$master:4040/api/v1/applications/$applicationId/allexecutors").asString
    val json = ujson.read(response.body)
    implicit val workerRW: ReadWriter[Worker] = upickle.default.macroRW[Worker]
    workers = upickle.default.read[Seq[Worker]](json)
    workers.foreach(x => {
      println("=================")
      println("id :" + x.id)
      println("hostPort:" + x.hostPort)
      println("totalCores:" + x.totalCores)
      println("maxMemory: " + x.maxMemory)
    })
    workers
  }

  // 获取 rdd 的大小和情况
  def getRdd(applicationId: String, master: String, rddId: Int): Value = {
    val response = Http(s"http://$master:4040/api/v1/applications/$applicationId/storage/rdd").asString
    println(response.body)
    val json = ujson.read(response.body)
    json
  }



}
