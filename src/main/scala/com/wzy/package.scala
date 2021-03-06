package com

import scala.collection.mutable

/**
 * 包共有对象
 */
package object wzy {

  case class Bucket(var partitionIndex: Int, var size: Int)

  case class Effect(var workerName: String, var capability: Int)

  case class Worker(var id: String, var hostPort: String, var totalCores: Int, var maxMemory: Double)

  implicit def allocationToPrefs(allocation: mutable.Map[Effect, Seq[Bucket]]): Map[Int, Seq[String]] = {
    var indexToPrefs: Map[Int, Seq[String]] = Map()
    for ((effect, buckets) <- allocation) {
      for(i <- buckets){
        indexToPrefs += (i.partitionIndex -> Seq(effect.workerName))
      }
    }
    indexToPrefs
  }

}

//使用样例类来构建协议

//Worker注册信息
case class RegisterWorkerInfo(id: String, CpuCores: Int, ram: Long)

@SerialVersionUID(123L)
class WorkerInfo(val id: String, val cpu: Int, val ram: Long) extends Serializable {
  // 新增属性：心跳时间
  var lastHeartBeatTime: Long = _

  // CPU使用率
  var lastCpuUsage: Float = _

  // 内存使用率
  var lastMemUsage: Float = _
}

// 当Worker注册成功，服务器返回一个RegisteredWorkerInfo对象
case object RegisteredWorkerInfo

// 每隔一定时间定时器发送给 Master 一个心跳
case class HeartBeat(id: String, cpuUsage: Float, memUsage: Float)

// Worker每隔一定时间定时器发送给自己一个消息
case object SendHeartBeat

// Master给自己发送一个触发检查超时Worker的信息
case object StartTimeOutWorker

// Master给自己发消息，检测Worker，对于心跳超时的
case object RemoveTimeOutWorker