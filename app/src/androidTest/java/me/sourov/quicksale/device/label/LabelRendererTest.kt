package me.sourov.quicksale.device.label

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.get
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.settings.LabelMedia
import me.sourov.quicksale.data.settings.LabelSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a printed label's barcode actually carries, checked by scanning the rendered bitmap the way
 * a reader at the counter would.
 *
 * Asserting the pixels rather than the inputs is the point: encoding is where a wrong symbology or
 * a rejected check digit turns into a label that looks right and scans as nothing.
 */
@RunWith(AndroidJUnit4::class)
class LabelRendererTest {

    private val renderer = LabelRenderer()

    @Test
    fun a_thirteen_digit_ean_prints_as_an_ean_13() {
        val result = scan(renderer.render(product(sku = "TSHIRT-001", ean = "4006381333931")))

        assertEquals("4006381333931", result.text)
        assertEquals(BarcodeFormat.EAN_13, result.barcodeFormat)
    }

    @Test
    fun an_eight_digit_ean_prints_as_an_ean_8() {
        val result = scan(renderer.render(product(sku = "MUG-CER", ean = "96385074")))

        assertEquals("96385074", result.text)
        assertEquals(BarcodeFormat.EAN_8, result.barcodeFormat)
    }

    /**
     * The barcode means the EAN, so a product without one prints no barcode at all. Encoding the
     * SKU instead would produce a label indistinguishable from a correct one that scans as a code
     * the counter can't sell — worse at the till than an obviously missing barcode.
     */
    @Test
    fun a_product_without_an_ean_prints_no_barcode_rather_than_its_sku() {
        val label = renderer.render(product(sku = "BAG-TOTE", ean = ""))

        assertThrows(NotFoundException::class.java) { scan(label) }
    }

    /**
     * A 13-digit number with the wrong check digit is not a valid EAN, and ZXing refuses to encode
     * it. The label must still carry the number the store holds rather than come out blank.
     */
    @Test
    fun a_number_that_is_not_a_valid_ean_still_prints_as_code_128() {
        val result = scan(renderer.render(product(sku = "ODD-001", ean = "4006381333930")))

        assertEquals("4006381333930", result.text)
        assertEquals(BarcodeFormat.CODE_128, result.barcodeFormat)
    }

    /**
     * The sharp case: a SKU that is itself 13 digits looks exactly like an EAN, and encoding it
     * would produce a label nobody could tell was wrong. Only the EAN field is ever encoded.
     */
    @Test
    fun a_sku_is_never_encoded_even_when_it_looks_like_an_ean() {
        val label = renderer.render(product(sku = "4006381333931", ean = ""))

        assertThrows(NotFoundException::class.java) { scan(label) }
    }

    /**
     * A long name shrinks to fit rather than running on, so the label grows by at most the one
     * extra line the name is allowed. Left unbounded it would push the barcode and price off the
     * label; truncated instead, it would name no product anyone could pick off a shelf.
     */
    @Test
    fun a_long_name_still_fits_the_labels_two_name_lines() {
        // Measured on continuous roll, where the bitmap is the content: a die-cut label is always
        // the full stock height, so it would report the same number whatever the name did.
        val roll = LabelSettings(media = LabelMedia.CONTINUOUS)
        val short = renderer.render(product(sku = "A", ean = "", name = "Tee"), roll).height
        val long = renderer.render(
            product(
                sku = "A",
                ean = "",
                name = "Stainless Steel Vacuum Insulated Wide Mouth Water Bottle 750ml, Arctic Blue",
            ),
            roll,
        ).height

        // At most one extra name line at full size, plus rounding slack.
        assertTrue(
            "a long name added ${long - short}px, more than the one extra line it may use",
            long - short <= 30,
        )
    }

    /**
     * The label stock is die cut at roughly 52 × 40 mm, so an over-tall bitmap does not simply look
     * wrong — it feeds across the gap and prints onto the next label. Every combination has to fit,
     * so this checks the worst one: every field on, with the longest name and a full EAN.
     */
    @Test
    fun a_label_with_everything_on_fits_the_stock() {
        val label = renderer.render(
            product(
                sku = "BOTTLE-750-ARCTIC-BLUE",
                ean = "4006381333931",
                name = "Stainless Steel Vacuum Insulated Wide Mouth Water Bottle 750ml, Arctic Blue",
            ),
            LabelSettings(
                showName = true,
                showBrand = true,
                showBarcode = true,
                showEanNumber = true,
                showSku = true,
                showPrice = true,
                showMsrp = true,
                media = LabelMedia.CONTINUOUS,
            ),
        )

        assertTrue(
            "label is ${label.height}px tall, past the ${LabelRenderer.MAX_HEIGHT_PX}px die cut",
            label.height <= LabelRenderer.MAX_HEIGHT_PX,
        )
        assertTrue(
            "label is ${label.width}px wide, past the ${LabelRenderer.LABEL_WIDTH_PX}px stock",
            label.width <= LabelRenderer.LABEL_WIDTH_PX,
        )
    }

    /**
     * The bottom of the label is kept blank on purpose, so there is room for a price-gun sticker or
     * a hand-written note. It is the reason the layout is as tight as it is, and the first thing a
     * new field would quietly eat, so the footer is asserted rather than left to the eye.
     */
    @Test
    fun a_label_with_everything_on_leaves_its_footer_blank() {
        LabelMedia.entries.forEach { media ->
            val label = renderer.render(
                product(
                    sku = "BOTTLE-750-ARCTIC-BLUE",
                    ean = "4006381333931",
                    name = "Stainless Steel Vacuum Insulated Wide Mouth Water Bottle 750ml, Arctic Blue",
                ),
                LabelSettings(media = media),
            )

            val footerTop = label.height - LabelRenderer.MIN_FOOTER_PX
            for (y in footerTop until label.height) {
                for (x in 0 until label.width) {
                    assertEquals(
                        "on $media the label prints at ($x, $y), inside its footer",
                        Color.WHITE,
                        label[x, y],
                    )
                }
            }
        }
    }

    /** A die-cut label is always the full stock height — the printer positions it, not the ink. */
    @Test
    fun a_die_cut_label_is_always_the_full_stock_height() {
        val label = renderer.render(
            product(sku = "TSHIRT-001", ean = "4006381333931"),
            LabelSettings(media = LabelMedia.DIE_CUT),
        )

        assertEquals(LabelRenderer.MAX_HEIGHT_PX, label.height)
    }

    /** Content hangs from the top of the label, so every product starts on the same line. */
    @Test
    fun labels_start_at_the_same_height_whichever_fields_they_have() {
        val full = renderer.render(product(sku = "TSHIRT-001", ean = "4006381333931"))
        val sparse = renderer.render(
            product(sku = "BAG-TOTE", ean = "", brand = ""),
            LabelSettings(showMsrp = false, showPrice = false),
        )

        assertEquals(firstPrintedRow(full), firstPrintedRow(sparse))
    }

    /** The first row of the bitmap carrying any ink, or -1 for a blank label. */
    private fun firstPrintedRow(label: Bitmap): Int {
        for (y in 0 until label.height) {
            for (x in 0 until label.width) {
                if (label[x, y] != Color.WHITE) return y
            }
        }
        return -1
    }

    /** Shrinking to fit must not cost the barcode its scannability — it is the point of the label. */
    @Test
    fun a_shrunk_label_still_scans() {
        val result = scan(
            renderer.render(
                product(
                    sku = "BOTTLE-750-ARCTIC-BLUE",
                    ean = "4006381333931",
                    name = "Stainless Steel Vacuum Insulated Wide Mouth Water Bottle 750ml, Arctic Blue",
                ),
                LabelSettings(),
            )
        )

        assertEquals("4006381333931", result.text)
    }

    /** Switching the barcode off leaves nothing scannable, EAN or not. */
    @Test
    fun the_barcode_switch_removes_it_entirely() {
        val label = renderer.render(
            product(sku = "TSHIRT-001", ean = "4006381333931"),
            LabelSettings(showBarcode = false),
        )

        assertThrows(NotFoundException::class.java) { scan(label) }
    }

    private fun product(
        sku: String,
        ean: String,
        name: String = "Classic Cotton T-Shirt",
        brand: String = "Northwind",
    ) = Product(
        id = 1,
        name = name,
        brand = brand,
        sku = sku,
        ean = ean,
        price = "19.99",
        regularPrice = "19.99",
        salePrice = "",
        msrp = "24.99",
        stockStatus = "instock",
        stockQuantity = 5,
        imageUrl = null,
        categories = "Apparel",
        description = "",
    )

    /** Reads the barcode back out of a rendered label, throwing when there is none to find. */
    private fun scan(label: Bitmap): Result {
        val pixels = IntArray(label.width * label.height)
        label.getPixels(pixels, 0, label.width, 0, 0, label.width, label.height)
        val source = RGBLuminanceSource(label.width, label.height, pixels)
        return MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.TRY_HARDER to true),
        )
    }
}
