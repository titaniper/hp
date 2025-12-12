package io.joopang.services.common.infrastructure.id

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SnowflakeIdGenerator(
    @Value("\${snowflake.datacenter-id:1}") private val configuredDatacenterId: Long,
    @Value("\${snowflake.worker-id:1}") private val configuredWorkerId: Long,
) {
    private val workerIdBits = 5L
    private val datacenterIdBits = 5L
    private val maxWorkerId = -1L xor (-1L shl workerIdBits.toInt())
    private val maxDatacenterId = -1L xor (-1L shl datacenterIdBits.toInt())
    private val sequenceBits = 12L

    private val workerIdShift = sequenceBits
    private val datacenterIdShift = sequenceBits + workerIdBits
    private val timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits
    private val sequenceMask = -1L xor (-1L shl sequenceBits.toInt())

    private val epoch = 1704067200000L // 2024-01-01 UTC

    private val datacenterId: Long = configuredDatacenterId.also { dc ->
        require(dc in 0..maxDatacenterId) { "Datacenter id must be between 0 and $maxDatacenterId" }
    }
    private val workerId: Long = configuredWorkerId.also { worker ->
        require(worker in 0..maxWorkerId) { "Worker id must be between 0 and $maxWorkerId" }
    }

    @Volatile
    private var sequence = 0L
    @Volatile
    private var lastTimestamp = -1L

    @Synchronized
    fun nextId(): Long {
        var timestamp = currentTime()

        if (timestamp < lastTimestamp) {
            throw IllegalStateException("Clock moved backwards. Refusing to generate id for ${lastTimestamp - timestamp}ms")
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) and sequenceMask
            if (sequence == 0L) {
                timestamp = waitUntilNextMillis(lastTimestamp)
            }
        } else {
            sequence = 0L
        }

        lastTimestamp = timestamp

        return ((timestamp - epoch) shl timestampLeftShift.toInt()) or
            (datacenterId shl datacenterIdShift.toInt()) or
            (workerId shl workerIdShift.toInt()) or
            sequence
    }

    private fun waitUntilNextMillis(lastTimestamp: Long): Long {
        var timestamp = currentTime()
        while (timestamp <= lastTimestamp) {
            timestamp = currentTime()
        }
        return timestamp
    }

    private fun currentTime(): Long = System.currentTimeMillis()
}
