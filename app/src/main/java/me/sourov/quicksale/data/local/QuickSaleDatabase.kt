package me.sourov.quicksale.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import me.sourov.quicksale.BuildConfig

@Database(
    entities = [Product::class, Customer::class],
    version = 3,
    exportSchema = false,
)
abstract class QuickSaleDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

        /** Populates a little sample data in debug builds so the screens are usable pre-sync. */
        private object SeedCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                if (!BuildConfig.DEBUG) return
                SAMPLE_PRODUCTS.forEach(db::execSQL)
                SAMPLE_CUSTOMERS.forEach(db::execSQL)
            }
        }

        private val SAMPLE_PRODUCTS = listOf(
            sampleProduct(1, "Classic Cotton T-Shirt", "TSHIRT-001", "19.99", "24.99", "19.99", "instock", 120, 11, "Apparel,Tops", "Soft 100% cotton tee with a relaxed fit. Pre-shrunk and machine washable."),
            sampleProduct(2, "Leather Card Wallet", "WALLET-002", "39.00", "39.00", "", "instock", 34, 22, "Accessories", "Slim full-grain leather wallet that holds up to six cards."),
            sampleProduct(3, "Stainless Water Bottle 750ml", "BOTTLE-750", "22.50", "22.50", "", "outofstock", 0, 33, "Drinkware", "Double-walled vacuum insulated bottle. Keeps drinks cold 24h."),
            sampleProduct(4, "Wireless Earbuds Pro", "AUDIO-EBP", "89.00", "109.00", "89.00", "instock", 18, 44, "Electronics,Audio", "Active noise cancelling earbuds with 30h total battery life."),
            sampleProduct(5, "Canvas Tote Bag", "BAG-TOTE", "14.00", "14.00", "", "instock", 75, 55, "Accessories,Bags", "Heavy-duty canvas tote, perfect for groceries or the beach."),
            sampleProduct(6, "Ceramic Coffee Mug", "MUG-CER", "11.25", "11.25", "", "onbackorder", 0, 66, "Drinkware", "Stoneware mug, 350ml, microwave and dishwasher safe."),
        )

        private val SAMPLE_CUSTOMERS = listOf(
            sampleCustomer(1, "Amelia", "Hughes", "amelia.hughes@example.com", "+1 202 555 0142", "Hughes Design", "Austin"),
            sampleCustomer(2, "Daniel", "Ortiz", "d.ortiz@example.com", "+1 312 555 0188", "", "Chicago"),
            sampleCustomer(3, "Priya", "Nair", "priya.nair@example.com", "+1 415 555 0119", "Nair Studio", "San Jose"),
            sampleCustomer(4, "Marcus", "Bennett", "marcus.b@example.com", "+1 646 555 0173", "", "Brooklyn"),
            sampleCustomer(5, "Sofia", "Lindgren", "sofia.l@example.com", "+46 70 555 0166", "Nordic Goods", "Malmo"),
        )

        private fun sampleProduct(
            id: Long, name: String, sku: String, price: String, regular: String, sale: String,
            stockStatus: String, qty: Int, imageSeed: Int, categories: String, description: String,
        ): String {
            val image = "https://picsum.photos/seed/$imageSeed/400/400"
            return "INSERT INTO products " +
                "(id, name, sku, price, regularPrice, salePrice, stockStatus, stockQuantity, imageUrl, categories, description) VALUES " +
                "($id, '$name', '$sku', '$price', '$regular', '$sale', '$stockStatus', $qty, '$image', '$categories', '$description')"
        }

        private fun sampleCustomer(
            id: Long, first: String, last: String, email: String, phone: String, company: String, city: String,
        ): String =
            "INSERT INTO customers (id, firstName, lastName, email, phone, company, city) VALUES " +
                "($id, '$first', '$last', '$email', '$phone', '$company', '$city')"
    }
}
