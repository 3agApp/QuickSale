package me.sourov.quicksale.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The line between "in the catalog" and "sellable".
 *
 * The till syncs the store's products in every status, so an unpublished one is a row like any
 * other and only the queries keep it off the counter. That makes this a pair of opposite risks
 * worth pinning: a draft that leaks into search can be sold by mistake, and a draft that is
 * invisible to a *scan* turns a one-click fix on the website into a hunt for a broken scanner.
 */
@RunWith(AndroidJUnit4::class)
class ProductStatusDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: QuickSaleDatabase
    private lateinit var dao: ProductDao

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, QuickSaleDatabase::class.java).build()
        dao = database.productDao()
        runBlocking {
            dao.replaceAll(
                listOf(
                    product(id = 1, name = "Classic Cotton T-Shirt", sku = "TSHIRT-001", ean = "4006381333931"),
                    product(id = 2, name = "Merino Beanie", sku = "BEANIE-MER", ean = "5012345678900", status = "draft"),
                    product(id = 3, name = "Merino Scarf", sku = "SCARF-MER", ean = "5012345678917", status = "pending"),
                    product(id = 4, name = "Merino Gloves", sku = "GLOVES-MER", ean = "5012345678924", status = "private"),
                )
            )
        }
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun search_returns_only_published_products() = runBlocking {
        val matches = dao.search("Merino").first()

        assertTrue("draft, pending and private products must not be searchable", matches.isEmpty())
        assertEquals(listOf(1L), dao.search("Cotton").first().map { it.id })
    }

    @Test
    fun the_browse_count_counts_only_published_products() = runBlocking {
        assertEquals(1, dao.countMatching("").first())
        assertEquals(0, dao.countMatching("Merino").first())
        assertEquals(1, dao.count().first())
    }

    /** The scan path is the one place an unpublished product must still be findable. */
    @Test
    fun an_exact_code_resolves_a_product_the_store_has_not_published() = runBlocking {
        val scanned = dao.findByCode("5012345678900")

        assertNotNull("a draft product's barcode must still resolve", scanned)
        assertEquals(2L, scanned?.id)
        assertFalse(scanned!!.isPublished)
        assertEquals("a draft", scanned.statusLabel)
    }

    @Test
    fun an_unpublished_product_resolves_by_sku_too() = runBlocking {
        assertEquals(3L, dao.findByCode("SCARF-MER")?.id)
    }

    /** A code the store has never carried stays a genuine miss, not an unpublished product. */
    @Test
    fun an_unknown_code_still_resolves_to_nothing() = runBlocking {
        assertNull(dao.findByCode("0000000000000"))
    }

    private fun product(
        id: Long,
        name: String,
        sku: String,
        ean: String,
        status: String = Product.STATUS_PUBLISHED,
    ) = Product(
        id = id,
        name = name,
        brand = "Bergen",
        sku = sku,
        ean = ean,
        price = "19.99",
        regularPrice = "19.99",
        salePrice = "",
        msrp = "",
        stockStatus = "instock",
        stockQuantity = 5,
        imageUrl = null,
        categories = "Apparel",
        description = "",
        status = status,
    )
}
