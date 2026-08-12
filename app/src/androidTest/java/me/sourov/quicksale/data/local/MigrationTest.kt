package me.sourov.quicksale.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the v4 → v5 upgrade, the change that turned the app from a WooCommerce-customer till into
 * a B2B one.
 *
 * This is the riskiest edit in the release: Room validates the schema every time it opens the
 * database, so a migration that produces even a slightly different shape than a fresh install
 * crashes on launch — and it crashes *only* for users who already had the app, which is everyone
 * in the field and nobody in a fresh test.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "migration-test.db"

    @Before
    fun clearDatabase() = context.deleteDatabase(databaseName).let { }

    @After
    fun cleanUp() = context.deleteDatabase(databaseName).let { }

    @Test
    fun migrates_from_v4_and_opens_cleanly() {
        seedVersion4Database()

        val database = openWithRoom()
        try {
            // Products survive the upgrade — only the customer model was replaced.
            val products = runBlocking { database.productDao().count().first() }
            assertEquals(1, products)

            // Opening at all is the real assertion: Room compares the migrated schema against
            // what the entities declare and throws if they differ in any column, type or index.
            val organizations = runBlocking { database.organizationDao().count().first() }
            assertEquals(0, organizations)
        } finally {
            database.close()
        }
    }

    @Test
    fun migrated_tables_accept_a_snapshot() {
        seedVersion4Database()

        val database = openWithRoom()
        try {
            val dao = database.organizationDao()
            runBlocking {
                dao.replaceAll(
                    organizations = listOf(sampleOrganization),
                    members = listOf(sampleMember),
                    locations = listOf(sampleLocation),
                )

                assertEquals(1, dao.count().first())
                assertEquals(1, dao.observeMembers(12).first().size)
                assertEquals(1, dao.observeLocations(12).first().size)

                val member = dao.observeMember(organizationId = 12, userId = 45).first()
                assertEquals("Grace Hopper", member?.name)
                // "all" round-trips as unrestricted rather than as an empty allow-list.
                assertEquals(null, member?.allowedLocationIds)

                val tally = dao.observeTallies().first().single()
                assertEquals(1, tally.memberCount)
                assertEquals(1, tally.locationCount)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun replaceAll_drops_whatever_the_new_snapshot_omits() {
        seedVersion4Database()

        val database = openWithRoom()
        try {
            val dao = database.organizationDao()
            runBlocking {
                dao.replaceAll(listOf(sampleOrganization), listOf(sampleMember), listOf(sampleLocation))
                // A snapshot answers deletions by omission — the second replace must not merge.
                dao.replaceAll(emptyList(), emptyList(), emptyList())

                assertEquals(0, dao.count().first())
                assertEquals(0, dao.observeMembers(12).first().size)
                assertEquals(0, dao.observeLocations(12).first().size)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun the_customers_table_is_gone_after_migrating() {
        seedVersion4Database()

        val database = openWithRoom()
        // Room opens lazily, so the migration only runs once something actually queries.
        runBlocking { database.organizationDao().count().first() }
        database.close()

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { raw ->
            raw.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='customers'",
                null,
            ).use { cursor ->
                assertFalse("customers should be dropped by the v5 migration", cursor.moveToFirst())
            }
        }
    }

    @Test
    fun products_that_predate_the_barcode_column_migrate_with_an_empty_one() {
        seedVersion4Database()

        val database = openWithRoom()
        try {
            runBlocking {
                val dao = database.productDao()
                val product = dao.observeById(1).first()
                assertEquals("TSHIRT-001", product?.sku)
                // v6 adds the column; it fills in on the next catalog sync, not during migration.
                assertEquals("", product?.ean)
                // Same for v7's MSRP, which most stores never send at all.
                assertEquals("", product?.msrp)
                // v9's pack size defaults to one unit ordered one at a time, so a row that
                // predates the columns stays orderable in exactly the quantities it always was.
                assertEquals(1, product?.minOrderQuantity)
                assertEquals(1, product?.orderQuantityStep)
                // v10's status defaults to published: a row synced before the app asked for the
                // status must stay sellable, or every till empties until it next reaches the store.
                assertEquals(Product.STATUS_PUBLISHED, product?.status)
                // A blank EAN must not swallow scans — the SKU still resolves the product.
                assertEquals(1L, dao.findByCode("TSHIRT-001")?.id)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun the_cart_survives_a_migration_and_a_reopen() {
        seedVersion4Database()

        // v11 adds the cart tables; an upgraded install must get them empty and usable, not
        // missing — a till that can't write its cart loses a sale on the next process death.
        val first = openWithRoom()
        try {
            runBlocking {
                val dao = first.cartDao()
                assertEquals(emptyList<CartLineRecord>(), dao.lines())
                assertEquals(null, dao.customer())

                dao.replace(
                    lines = listOf(CartLineRecord(productId = 1, quantity = 6, addedAtMillis = 0)),
                    customer = CartCustomerRecord(organizationId = 12, memberUserId = 45),
                )
            }
        } finally {
            first.close()
        }

        // Reopened, because surviving the *process* is the entire point of the table.
        val second = openWithRoom()
        try {
            runBlocking {
                val dao = second.cartDao()
                assertEquals(listOf(CartLineRecord(1, 6, 0)), dao.lines())
                assertEquals(12L, dao.customer()?.organizationId)
                assertEquals(45L, dao.customer()?.memberUserId)

                // Replacing with an empty cart is how "clear" is expressed, and it must leave
                // nothing behind for the next order to inherit.
                dao.replace(lines = emptyList(), customer = null)
                assertEquals(emptyList<CartLineRecord>(), dao.lines())
                assertEquals(null, dao.customer())
            }
        } finally {
            second.close()
        }
    }

    /** Builds the database exactly as version 4 left it, including a row worth preserving. */
    private fun seedVersion4Database() {
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`sku` TEXT NOT NULL, `price` TEXT NOT NULL, `regularPrice` TEXT NOT NULL, " +
                    "`salePrice` TEXT NOT NULL, `stockStatus` TEXT NOT NULL, `stockQuantity` INTEGER, " +
                    "`imageUrl` TEXT, `categories` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `customers` (`id` INTEGER NOT NULL, `firstName` TEXT NOT NULL, " +
                    "`lastName` TEXT NOT NULL, `email` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                    "`company` TEXT NOT NULL, `city` TEXT NOT NULL, " +
                    "`billingJson` TEXT NOT NULL DEFAULT '', `shippingJson` TEXT NOT NULL DEFAULT '', " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "INSERT INTO products (id, name, sku, price, regularPrice, salePrice, stockStatus, " +
                    "stockQuantity, imageUrl, categories, description) VALUES " +
                    "(1, 'Tee', 'TSHIRT-001', '19.99', '19.99', '', 'instock', 5, NULL, 'Apparel', '')"
            )
            db.execSQL(
                "INSERT INTO customers (id, firstName, lastName, email, phone, company, city) VALUES " +
                    "(1, 'Amelia', 'Hughes', 'a@example.com', '', '', 'Austin')"
            )
            db.version = 4
        }
    }

    private fun openWithRoom(): QuickSaleDatabase =
        Room.databaseBuilder(context, QuickSaleDatabase::class.java, databaseName)
            .addMigrations(
                QuickSaleDatabase.MIGRATION_4_5,
                QuickSaleDatabase.MIGRATION_5_6,
                QuickSaleDatabase.MIGRATION_6_7,
                QuickSaleDatabase.MIGRATION_7_8,
                QuickSaleDatabase.MIGRATION_8_9,
                QuickSaleDatabase.MIGRATION_9_10,
                QuickSaleDatabase.MIGRATION_10_11,
                QuickSaleDatabase.MIGRATION_11_13,
                QuickSaleDatabase.MIGRATION_12_13,
            )
            .build()

    private val sampleOrganization = Organization(
        id = 12,
        name = "Acme GmbH",
        status = "active",
        allowCustomShipping = true,
        billingJson = "{}",
        billingFormatted = "Acme GmbH\n1 Hauptstrasse\n10115 Berlin",
        email = "buy@acme.example",
        phone = "+49 30 000000",
        city = "Berlin",
        country = "DE",
        dateModifiedGmt = "2026-08-09 03:51:08",
    )

    private val sampleMember = Member(
        memberId = 7,
        organizationId = 12,
        userId = 45,
        name = "Grace Hopper",
        email = "grace@acme.example",
        role = "admin",
        status = "active",
        canPlaceOrders = true,
        locationAccess = Member.LOCATION_ACCESS_ALL,
    )

    private val sampleLocation = OrgLocation(
        id = 3,
        organizationId = 12,
        name = "Warehouse North",
        isDefault = true,
        formatted = "Grace Hopper\n9 Lagerweg\n20095 Hamburg",
        firstName = "Grace",
        lastName = "Hopper",
        company = "",
        address1 = "9 Lagerweg",
        address2 = "",
        city = "Hamburg",
        state = "",
        postcode = "20095",
        country = "DE",
        phone = "+49 40 123456",
    )
}
