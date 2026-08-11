package me.sourov.quicksale.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.sourov.quicksale.BuildConfig

@Database(
    entities = [Product::class, Organization::class, Member::class, OrgLocation::class],
    version = 7,
    exportSchema = false,
)
abstract class QuickSaleDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun organizationDao(): OrganizationDao

    companion object {
        @Volatile
        private var instance: QuickSaleDatabase? = null

        fun getInstance(context: Context): QuickSaleDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): QuickSaleDatabase =
            Room.databaseBuilder(context, QuickSaleDatabase::class.java, "quicksale.db")
                .addCallback(SeedCallback)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7,
                )
                .build()

        /** v2 once stored orders locally; v3 drops them (orders go straight to WooCommerce now). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS orders (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "remoteId INTEGER, customerId INTEGER NOT NULL, customerName TEXT NOT NULL, " +
                        "status TEXT NOT NULL, total TEXT NOT NULL, createdAt INTEGER NOT NULL, " +
                        "synced INTEGER NOT NULL, syncError TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS order_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, orderId INTEGER NOT NULL, " +
                        "productId INTEGER NOT NULL, productName TEXT NOT NULL, price TEXT NOT NULL, " +
                        "quantity INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS order_items")
                db.execSQL("DROP TABLE IF EXISTS orders")
            }
        }

        /** v4 stored customers' full billing/shipping addresses for order creation. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN billingJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN shippingJson TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v5 moves the app to the store's B2B model. WooCommerce customers are replaced by
         * organizations, each with its members and its delivery locations — on an organization
         * shop `/wc/v3/customers` returns only the shop's own staff, so nothing in the old table
         * was usable at the counter. It is dropped rather than migrated; the organization snapshot
         * refills the app on the first sync.
         */
        @VisibleForTesting
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS customers")
                ORGANIZATION_SCHEMA.forEach(db::execSQL)
            }
        }

        /**
         * v6 stores each product's barcode number (EAN/GTIN) alongside its SKU, so scans and
         * searches resolve on it and labels encode it instead of the SKU. Products keep their rows;
         * the column fills in on the next catalog sync.
         */
        @VisibleForTesting
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN ean TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v7 stores the manufacturer's suggested retail price, for stores whose catalog carries one
         * (`msrp` is a plugin field, not WooCommerce core). Products keep their rows; the column
         * fills in on the next catalog sync, and stays empty on a store that doesn't send it.
         */
        @VisibleForTesting
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN msrp TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * The organization tables, copied verbatim from the statements Room generates for a fresh
         * install (`QuickSaleDatabase_Impl.createAllTables`).
         *
         * Keeping them identical rather than hand-equivalent matters: Room validates the schema
         * every time it opens the database, and an upgraded install that differs from a fresh one
         * in any detail crashes on launch for exactly the users who already had the app.
         */
        private val ORGANIZATION_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `organizations` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `status` TEXT NOT NULL, `allowCustomShipping` INTEGER NOT NULL, `billingJson` TEXT NOT NULL, `billingFormatted` TEXT NOT NULL, `email` TEXT NOT NULL, `phone` TEXT NOT NULL, `city` TEXT NOT NULL, `country` TEXT NOT NULL, `dateModifiedGmt` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `org_members` (`memberId` INTEGER NOT NULL, `organizationId` INTEGER NOT NULL, `userId` INTEGER NOT NULL, `name` TEXT NOT NULL, `email` TEXT NOT NULL, `role` TEXT NOT NULL, `status` TEXT NOT NULL, `canPlaceOrders` INTEGER NOT NULL, `locationAccess` TEXT NOT NULL, PRIMARY KEY(`memberId`))",
            "CREATE INDEX IF NOT EXISTS `index_org_members_organizationId` ON `org_members` (`organizationId`)",
            "CREATE TABLE IF NOT EXISTS `org_locations` (`id` INTEGER NOT NULL, `organizationId` INTEGER NOT NULL, `name` TEXT NOT NULL, `isDefault` INTEGER NOT NULL, `formatted` TEXT NOT NULL, `firstName` TEXT NOT NULL, `lastName` TEXT NOT NULL, `company` TEXT NOT NULL, `address1` TEXT NOT NULL, `address2` TEXT NOT NULL, `city` TEXT NOT NULL, `state` TEXT NOT NULL, `postcode` TEXT NOT NULL, `country` TEXT NOT NULL, `phone` TEXT NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_org_locations_organizationId` ON `org_locations` (`organizationId`)",
        )

        /** Populates a little sample data in debug builds so the screens are usable pre-sync. */
        private object SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                if (!BuildConfig.DEBUG) return
                SAMPLE_PRODUCTS.forEach(db::execSQL)
                SAMPLE_ORGANIZATIONS.forEach(db::execSQL)
            }
        }

        private val SAMPLE_PRODUCTS = listOf(
            sampleProduct(1, "Classic Cotton T-Shirt", "TSHIRT-001", "4006381333931", "19.99", "24.99", "19.99", "24.99", "instock", 120, 11, "Apparel,Tops", "Soft 100% cotton tee with a relaxed fit. Pre-shrunk and machine washable."),
            sampleProduct(2, "Leather Card Wallet", "WALLET-002", "5901234123457", "39.00", "39.00", "", "", "instock", 34, 22, "Accessories", "Slim full-grain leather wallet that holds up to six cards."),
            // A title long enough to need the label's name to shrink below its full size.
            sampleProduct(3, "Stainless Steel Vacuum Insulated Water Bottle 750ml", "BOTTLE-750", "7622210992659", "22.50", "22.50", "", "27.00", "outofstock", 0, 33, "Drinkware", "Double-walled vacuum insulated bottle. Keeps drinks cold 24h."),
            sampleProduct(4, "Wireless Earbuds Pro", "AUDIO-EBP", "0190198001787", "89.00", "109.00", "89.00", "129.00", "instock", 18, 44, "Electronics,Audio", "Active noise cancelling earbuds with 30h total battery life."),
            // No barcode number on the store: its label falls back to the SKU.
            sampleProduct(5, "Canvas Tote Bag", "BAG-TOTE", "", "14.00", "14.00", "", "", "instock", 75, 55, "Accessories,Bags", "Heavy-duty canvas tote, perfect for groceries or the beach."),
            sampleProduct(6, "Ceramic Coffee Mug", "MUG-CER", "96385074", "11.25", "11.25", "", "", "onbackorder", 0, 66, "Drinkware", "Stoneware mug, 350ml, microwave and dishwasher safe."),
        )

        /**
         * Four organizations covering the states the counter has to handle differently: one trading
         * normally, one awaiting approval, one suspended, and one whose single member sends a tap
         * straight to a new order instead of to the detail screen.
         */
        private val SAMPLE_ORGANIZATIONS = listOf(
            sampleOrganization(12, "Acme GmbH", "active", true, "buy@acme.example", "+49 30 000000", "Berlin", "DE", "Acme GmbH\nAda Byron\n1 Hauptstrasse\n10115 Berlin"),
            sampleOrganization(13, "Northwind Traders", "pending", false, "orders@northwind.example", "+44 20 7946 0000", "London", "GB", "Northwind Traders\nJoan Clarke\n7 Bishopsgate\nLondon EC2N 3AR"),
            sampleOrganization(14, "Lindgren Nordic AB", "suspended", false, "info@lindgren.example", "+46 70 555 0166", "Malmo", "SE", "Lindgren Nordic AB\nSofia Lindgren\nStora Nygatan 12\n211 37 Malmo"),
            sampleOrganization(15, "Bergen Kaffe AS", "active", false, "post@bergenkaffe.example", "+47 55 55 00 12", "Bergen", "NO", "Bergen Kaffe AS\nIngrid Dahl\nStrandgaten 4\n5013 Bergen"),

            sampleMember(7, 12, 45, "Grace Hopper", "grace@acme.example", "admin", "active", true, "all"),
            sampleMember(8, 12, 46, "Alan Turing", "alan@acme.example", "member", "active", true, "3"),
            sampleMember(9, 12, 47, "Katherine Johnson", "kj@acme.example", "member", "inactive", false, "all"),
            sampleMember(10, 13, 48, "Joan Clarke", "joan@northwind.example", "admin", "active", false, "all"),
            sampleMember(11, 14, 49, "Sofia Lindgren", "sofia@lindgren.example", "admin", "active", false, "all"),
            // The only member of an active account: tapping the row opens her order directly.
            sampleMember(12, 15, 50, "Ingrid Dahl", "ingrid@bergenkaffe.example", "admin", "active", true, "all"),

            sampleLocation(3, 12, "Warehouse North", true, "Grace Hopper\n9 Lagerweg\n20095 Hamburg", "Grace", "Hopper", "", "9 Lagerweg", "", "Hamburg", "", "20095", "DE", "+49 40 123456"),
            sampleLocation(4, 12, "Berlin Office", false, "Acme GmbH\n1 Hauptstrasse\n10115 Berlin", "Ada", "Byron", "Acme GmbH", "1 Hauptstrasse", "", "Berlin", "", "10115", "DE", "+49 30 000000"),
            sampleLocation(5, 13, "Bishopsgate", true, "Joan Clarke\n7 Bishopsgate\nLondon EC2N 3AR", "Joan", "Clarke", "", "7 Bishopsgate", "", "London", "", "EC2N 3AR", "GB", "+44 20 7946 0000"),
            sampleLocation(6, 15, "Roastery", true, "Ingrid Dahl\nStrandgaten 4\n5013 Bergen", "Ingrid", "Dahl", "Bergen Kaffe AS", "Strandgaten 4", "", "Bergen", "", "5013", "NO", "+47 55 55 00 12"),
        )

        private fun sampleProduct(
            id: Long, name: String, sku: String, ean: String, price: String, regular: String,
            sale: String, msrp: String, stockStatus: String, qty: Int, imageSeed: Int,
            categories: String, description: String,
        ): String {
            val image = "https://picsum.photos/seed/$imageSeed/400/400"
            return "INSERT INTO products " +
                "(id, name, sku, ean, price, regularPrice, salePrice, msrp, stockStatus, stockQuantity, imageUrl, categories, description) VALUES " +
                "($id, '$name', '$sku', '$ean', '$price', '$regular', '$sale', '$msrp', '$stockStatus', $qty, '$image', '$categories', '$description')"
        }

        private fun sampleOrganization(
            id: Long, name: String, status: String, allowCustomShipping: Boolean,
            email: String, phone: String, city: String, country: String, formatted: String,
        ): String =
            "INSERT INTO organizations (id, name, status, allowCustomShipping, billingJson, " +
                "billingFormatted, email, phone, city, country, dateModifiedGmt) VALUES " +
                "($id, '$name', '$status', ${allowCustomShipping.asSqlBoolean()}, '{}', " +
                "'${formatted.sqlEscaped()}', '$email', '$phone', '$city', '$country', '2026-08-09 03:51:08')"

        private fun sampleMember(
            memberId: Long, organizationId: Long, userId: Long, name: String, email: String,
            role: String, status: String, canPlaceOrders: Boolean, locationAccess: String,
        ): String =
            "INSERT INTO org_members (memberId, organizationId, userId, name, email, role, status, " +
                "canPlaceOrders, locationAccess) VALUES " +
                "($memberId, $organizationId, $userId, '$name', '$email', '$role', '$status', " +
                "${canPlaceOrders.asSqlBoolean()}, '$locationAccess')"

        private fun sampleLocation(
            id: Long, organizationId: Long, name: String, isDefault: Boolean, formatted: String,
            firstName: String, lastName: String, company: String, address1: String,
            address2: String, city: String, state: String, postcode: String, country: String,
            phone: String,
        ): String =
            "INSERT INTO org_locations (id, organizationId, name, isDefault, formatted, firstName, " +
                "lastName, company, address1, address2, city, state, postcode, country, phone) VALUES " +
                "($id, $organizationId, '$name', ${isDefault.asSqlBoolean()}, '${formatted.sqlEscaped()}', " +
                "'$firstName', '$lastName', '$company', '$address1', '$address2', '$city', " +
                "'$state', '$postcode', '$country', '$phone')"

        private fun Boolean.asSqlBoolean(): Int = if (this) 1 else 0

        private fun String.sqlEscaped(): String = replace("'", "''")
    }
}
