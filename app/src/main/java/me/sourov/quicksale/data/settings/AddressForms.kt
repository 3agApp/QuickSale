package me.sourov.quicksale.data.settings

/**
 * One field of a country's shipping address form, exactly as WooCommerce defines it for that
 * country with the shop's own checkout customisations applied.
 *
 * The app renders from this rather than hand-writing an address form, because a hand-written one
 * is wrong in a different way in every country.
 */
data class AddressField(
    /** Submitted under this name inside the order's `shipping` block. */
    val name: String,
    val label: String,
    /**
     * Taken from the served form and never second-guessed against WooCommerce's published
     * defaults. The shop's own answer differs from them on purpose: `last_name` is never required
     * (a delivery address belongs to a place as often as to a person — "Warehouse North" has no
     * surname, so the place name goes in `first_name`), and neither is `phone`, whatever the
     * checkout's phone setting says, because that setting is a rule about the person buying.
     * Marking either required here would refuse an address the shop's own checkout accepts.
     */
    val required: Boolean,
    val hidden: Boolean,
    /** `text`, `tel`, `country`, `state`, … — drives the keyboard and the control. */
    val type: String,
    /**
     * Present only on `state`, and only where the country has a fixed list. When present render a
     * picker and submit the key; when absent free text is what the checkout renders too.
     */
    val options: Map<String, String> = emptyMap(),
) {
    val hasOptions: Boolean get() = options.isNotEmpty()
}

/**
 * The shop's shipping address forms, one per country it ships to. Synced alongside the
 * organization snapshot and revalidated with an ETag; it changes only when WooCommerce or the
 * shop's settings do.
 *
 * Only shipping forms exist here: a one-off delivery address is the only address the till ever
 * composes. Billing comes from the organization row and the server writes it itself.
 */
data class AddressForms(
    /** The shop's base country, for preselecting. */
    val defaultCountry: String = "",
    /** The shop's ship-to list (code → name), not every country in the world. */
    val countries: Map<String, String> = emptyMap(),
    val forms: Map<String, List<AddressField>> = emptyMap(),
) {
    val isEmpty: Boolean get() = countries.isEmpty() || forms.isEmpty()

    /** Countries the shop ships to, in name order, ready for a picker. */
    val countryChoices: List<Pair<String, String>>
        get() = countries.entries
            .map { it.key to it.value }
            .sortedBy { it.second.lowercase() }

    /** The visible fields for [countryCode], in WooCommerce's display order. */
    fun fieldsFor(countryCode: String): List<AddressField> =
        forms[countryCode].orEmpty().filterNot { it.hidden }

    fun countryName(code: String): String = countries[code] ?: code
}
