package com.tcodeplayer.app.probe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 同目录文件访问抽象：网络驱动器与本地使用完全相同的探测逻辑。 */
interface DirectoryListing {
    suspend fun names(): List<String>
    suspend fun readText(name: String): String?
    fun uriOf(name: String): String
}

class LocalDirectoryListing(private val directory: File) : DirectoryListing {
    override suspend fun names(): List<String> = withContext(Dispatchers.IO) {
        directory.listFiles()?.filter { it.isFile }?.map { it.name } ?: emptyList()
    }

    override suspend fun readText(name: String): String? = withContext(Dispatchers.IO) {
        runCatching { File(directory, name).readText(Charsets.UTF_8) }.getOrNull()
    }

    override fun uriOf(name: String): String = File(directory, name).toURI().toString()
}

/**
 * mpvEx 文件浏览层适配接口。合并进 fork 时，用其 SMB/FTP/WebDAV 实现替换
 * NoOpMpvBrowserFacade；探测逻辑本身不区分本地与网络。
 */
interface MpvBrowserFacade {
    suspend fun listNames(directoryUri: String): List<String>
    suspend fun readText(fileUri: String): String?
    fun buildUri(directoryUri: String, name: String): String
}

/** 默认实现：静默降级，不弹错误、不崩溃、不阻塞播放。 */
class NoOpMpvBrowserFacade : MpvBrowserFacade {
    override suspend fun listNames(directoryUri: String): List<String> = emptyList()
    override suspend fun readText(fileUri: String): String? = null
    override fun buildUri(directoryUri: String, name: String): String = "$directoryUri/$name"
}

class NetworkDirectoryListing(
    private val directoryUri: String,
    private val browser: MpvBrowserFacade,
) : DirectoryListing {
    override suspend fun names(): List<String> =
        runCatching { browser.listNames(directoryUri) }.getOrDefault(emptyList())

    override suspend fun readText(name: String): String? =
        runCatching { browser.readText(browser.buildUri(directoryUri, name)) }.getOrNull()

    override fun uriOf(name: String): String = browser.buildUri(directoryUri, name)
}
