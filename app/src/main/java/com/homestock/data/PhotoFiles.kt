package com.homestock.data

import java.io.File
import java.util.UUID

/** Purchase screenshots live under filesDir/purchase_photos/<batchId>/<imageId>.jpg. */
class PhotoFiles(private val root: File) {
    fun relativePath(purchaseId: Long, imageId: String = UUID.randomUUID().toString()): String =
        "purchase_photos/$purchaseId/$imageId.jpg"

    fun file(relativePath: String): File = File(root, relativePath)

    fun write(purchaseId: Long, bytes: ByteArray, imageId: String = UUID.randomUUID().toString()): String {
        val relative = relativePath(purchaseId, imageId)
        val dest = file(relative)
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        return relative
    }

    fun exists(relativePath: String): Boolean = file(relativePath).isFile

    fun deleteRelative(relativePath: String) {
        file(relativePath).delete()
    }

    fun deletePurchase(purchaseId: Long) {
        File(root, "purchase_photos/$purchaseId").deleteRecursively()
    }
}
