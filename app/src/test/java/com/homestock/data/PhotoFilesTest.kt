package com.homestock.data

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFilesTest {
    @Test
    fun copiesIntoThePurchaseDirectoryAndDeletesOnlyThatPurchase() {
        val root = Files.createTempDirectory("photos").toFile()
        val store = PhotoFiles(root)
        val path = store.write(purchaseId = 4, bytes = byteArrayOf(1, 2, 3), imageId = "shot")
        assertEquals("purchase_photos/4/shot.jpg", path)
        assertTrue(store.exists(path))
        assertEquals(listOf<Byte>(1, 2, 3), store.file(path).readBytes().toList())

        store.deletePurchase(4)
        assertFalse(store.exists(path))
        assertFalse(store.file(path).exists())
    }
}
