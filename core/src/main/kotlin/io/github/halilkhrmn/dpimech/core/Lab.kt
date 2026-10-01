package io.github.halilkhrmn.dpimech.core

/** A strategy offered to the Strategy Lab. */
data class LabStrategy(
    val name: String,
    val args: String,
    /** "DPIMech standard set" or an online list's label. */
    val source: String,
    /** Where an online strategy was downloaded from; empty for the standard set. */
    val origin: String = "",
    /** Preset made for the user's detected ISP. */
    val recommended: Boolean = false,
)

data class LabResult(
    /** `null` is the baseline run without any bypass. */
    val strategy: LabStrategy?,
    val ok: Int,
    val total: Int,
    /** Mean time to first response byte of successful requests. */
    val avgMs: Int,
    val failedDomains: List<String> = emptyList(),
    /** Set when the engine itself could not start with this strategy. */
    val error: String? = null,
    /**
     * Passed the extra rounds run for the best candidates (every site, every time); a single
     * lucky round is not enough to call a strategy working.
     */
    val confirmed: Boolean = false,
) : Comparable<LabResult> {

    /** Success rate in per mille. */
    val rate: Int get() = if (total == 0) 0 else ok * 1000 / total

    /** Higher is better: confirmed first, then success rate, then speed (same as desktop). */
    override fun compareTo(other: LabResult): Int = compareValuesBy(
        this, other, { it.confirmed }, { it.rate }, { -it.avgMs.toLong() },
    )

    fun sameStrategy(other: LabResult): Boolean {
        val a = strategy ?: return false
        val b = other.strategy ?: return false
        return a.name == b.name && a.args == b.args
    }

    companion object {
        const val STANDARD_SET = "DPIMech standard set"

        /** The winner among strategy runs (baseline and failed starts excluded), if any site opened. */
        fun best(results: List<LabResult>): LabResult? = results
            .filter { it.strategy != null && it.error == null && it.ok > 0 }
            .maxOrNull()
    }
}
