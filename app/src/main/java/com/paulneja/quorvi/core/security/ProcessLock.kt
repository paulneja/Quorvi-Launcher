package com.paulneja.quorvi.core.security

import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ProcessLock {
    suspend fun <T> withLock(block: suspend () -> T): T

    companion object {
        val None = object : ProcessLock {
            override suspend fun <T> withLock(block: suspend () -> T): T = block()
        }
    }
}

class FileProcessLock(private val file: File) : ProcessLock {
    override suspend fun <T> withLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { raf ->
            raf.channel.lock().use { block() }
        }
    }
}
