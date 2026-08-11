package me.sourov.quicksale.device.label

import android.graphics.Bitmap
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
     * extra line the name is allowed. Left unbounded it would push the barcode and price down the
     * roll; truncated instead, it would name no product anyone could pick off a shelf.
     */
    @Test
    fun a_long_name_still_fits_the_labels_two_name_lines() {
        val short = renderer.render(product(sku = "A", ean = "", name = "Tee")).height
        val long = renderer.render(
            product(
                sku = "A",
                ean = "",
                name = "Stainless Steel Vacuum Insulated Wide Mouth Water Bottle 750ml, Arctic Blue",
            )
        ).height

        // One name line at full size, plus a pixel of rounding slack.
        assertTrue(
            "a long name added ${long - short}px, more than the one extra line it may use",
            long - short <= 25,
        )
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

    private fun product(sku: String, ean: String, name: String = "Classic Cotton T-Shirt") = Product(
        id = 1,
        name = name,
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
