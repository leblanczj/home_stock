package com.homestock.domain

private data class Weighted(val rate: Rational, val weight: Int)

fun computeRates(observations: List<DayObservation>, explicit: List<ExplicitUse>): RateModel {
    val usable = observations.filter { it.q > Rational.ZERO && it.days.values.any { days -> days > 0 } }
    val scheme1 = mutableMapOf<ResidentSet, MutableList<Weighted>>()
    val scheme2 = mutableMapOf<ResidentSet, MutableList<Weighted>>()
    val scheme3 = mutableMapOf<ResidentSet, MutableList<Weighted>>()
    val scheme4 = mutableMapOf<ResidentSet, MutableList<Weighted>>()
    val allocations = mutableListOf<Allocation>()

    if (usable.isNotEmpty()) {
        val sets = usable.flatMap { it.days.filterValues { d -> d > 0 }.keys }.toSet()
        val parent = sets.associateWith { it }.toMutableMap()
        fun find(x: ResidentSet): ResidentSet {
            var root = x
            while (parent.getValue(root) != root) root = parent.getValue(root)
            var cursor = x
            while (cursor != root) {
                val next = parent.getValue(cursor)
                parent[cursor] = root
                cursor = next
            }
            return root
        }
        fun union(a: ResidentSet, b: ResidentSet) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }
        for (obs in usable) {
            val present = obs.days.filterValues { it > 0 }.keys.toList()
            for (i in 1 until present.size) union(present[0], present[i])
        }
        val groups = usable.groupBy { obs ->
            val present = obs.days.filterValues { it > 0 }.keys
            find(present.first())
        }
        for (group in groups.values) {
            val groupSets = group.flatMap { it.days.filterValues { d -> d > 0 }.keys }.toSet().sorted()
            if (groupSets.size == 2 && distinctProportions(group, groupSets[0], groupSets[1]) >= 2) {
                if (!solvePair(group, groupSets[0], groupSets[1], scheme3, allocations)) {
                    applySimple(group, scheme1, scheme2, allocations)
                }
            } else {
                applySimple(group, scheme1, scheme2, allocations)
            }
        }
    }

    val byDate = explicit.groupBy { it.set to it.date }
    val bySet = byDate.keys.groupBy({ it.first }, { it.second })
    for ((set, dates) in bySet) {
        val distinct = dates.distinct()
        val qty = distinct.fold(Rational.ZERO) { acc, date ->
            acc + byDate.getValue(set to date).fold(Rational.ZERO) { q, use -> q + use.quantity }
        }
        val days = distinct.size
        if (days > 0 && qty > Rational.ZERO) {
            scheme4.getOrPut(set) { mutableListOf() }
                .add(Weighted(qty / Rational.of(days.toLong()), days))
        }
    }

    val all = (scheme1.keys + scheme2.keys + scheme3.keys + scheme4.keys).toSet()
    val bySetRate = all.associateWith { set ->
        val parts = listOf(
            1 to merge(scheme1[set]),
            2 to merge(scheme2[set]),
            3 to merge(scheme3[set]),
            4 to merge(scheme4[set]),
        )
        val present = parts.mapNotNull { (scheme, merged) -> merged?.let { scheme to it } }
        val weights = SchemeWeights(
            scheme1 = scheme1[set]?.sumOf { it.weight } ?: 0,
            scheme2 = scheme2[set]?.sumOf { it.weight } ?: 0,
            scheme3 = scheme3[set]?.sumOf { it.weight } ?: 0,
            scheme4 = scheme4[set]?.sumOf { it.weight } ?: 0,
        )
        if (present.isEmpty()) {
            SetRate(null, weights, emptyMap())
        } else {
            val total = present.sumOf { it.second.weight }
            var acc = Rational.ZERO
            for ((_, weighted) in present) {
                acc += weighted.rate * Rational.of(weighted.weight.toLong())
            }
            SetRate(
                rate = acc / Rational.of(total.toLong()),
                weights = weights,
                schemeRates = present.associate { it.first to it.second.rate },
            )
        }
    }
    return RateModel(bySetRate, allocations)
}

private fun distinctProportions(group: List<DayObservation>, left: ResidentSet, right: ResidentSet): Int =
    group.map { proportion(it.days[left] ?: 0, it.days[right] ?: 0) }.toSet().size

private fun proportion(d: Int, e: Int): Pair<Int, Int> {
    val g = gcd(d, e)
    return d / g to e / g
}

private fun gcd(a: Int, b: Int): Int {
    var x = kotlin.math.abs(a)
    var y = kotlin.math.abs(b)
    while (y != 0) {
        val t = x % y
        x = y
        y = t
    }
    return if (x == 0) 1 else x
}

/**
 * @return false when delta is 0 and the caller should fall back to schemes 1 and 2.
 */
private fun solvePair(
    group: List<DayObservation>,
    setD: ResidentSet,
    setE: ResidentSet,
    scheme3: MutableMap<ResidentSet, MutableList<Weighted>>,
    allocations: MutableList<Allocation>,
): Boolean {
    var sdd = 0L
    var see = 0L
    var sde = 0L
    var td = Rational.ZERO
    var te = Rational.ZERO
    for (obs in group) {
        val d = (obs.days[setD] ?: 0).toLong()
        val e = (obs.days[setE] ?: 0).toLong()
        sdd += d * d
        see += e * e
        sde += d * e
        td += obs.q * Rational.of(d)
        te += obs.q * Rational.of(e)
    }
    val delta = Rational.of(sdd) * Rational.of(see) - Rational.of(sde) * Rational.of(sde)
    if (delta.isZero()) return false
    var rd = (td * Rational.of(see) - te * Rational.of(sde)) / delta
    var re = (te * Rational.of(sdd) - td * Rational.of(sde)) / delta
    when {
        rd < Rational.ZERO && re < Rational.ZERO -> {
            rd = Rational.ZERO
            re = Rational.ZERO
        }
        rd < Rational.ZERO -> {
            rd = Rational.ZERO
            re = if (see == 0L) Rational.ZERO else te / Rational.of(see)
        }
        re < Rational.ZERO -> {
            re = Rational.ZERO
            rd = if (sdd == 0L) Rational.ZERO else td / Rational.of(sdd)
        }
    }
    val weight = group.sumOf { it.days.values.sum() }
    scheme3.getOrPut(setD) { mutableListOf() }.add(Weighted(rd, weight))
    scheme3.getOrPut(setE) { mutableListOf() }.add(Weighted(re, weight))
    val rates = mapOf(setD to rd, setE to re)
    for (obs in group) {
        val present = obs.days.filterValues { it > 0 }
        val totalDays = present.values.sum()
        val denom = present.entries.fold(Rational.ZERO) { acc, (set, days) ->
            acc + Rational.of(days.toLong()) * rates.getValue(set)
        }
        val shares = if (denom.isZero()) {
            present.mapValues { (_, days) ->
                obs.q * Rational.of(days.toLong()) / Rational.of(totalDays.toLong())
            }
        } else {
            present.mapValues { (set, days) ->
                obs.q * (Rational.of(days.toLong()) * rates.getValue(set)) / denom
            }
        }
        allocations += Allocation(obs.countId, 3, shares)
    }
    return true
}

private fun applySimple(
    group: List<DayObservation>,
    scheme1: MutableMap<ResidentSet, MutableList<Weighted>>,
    scheme2: MutableMap<ResidentSet, MutableList<Weighted>>,
    allocations: MutableList<Allocation>,
) {
    for (obs in group) {
        val present = obs.days.filterValues { it > 0 }
        val total = present.values.sum()
        if (total == 0) continue
        val rate = obs.q / Rational.of(total.toLong())
        if (present.size == 1) {
            val set = present.keys.first()
            scheme1.getOrPut(set) { mutableListOf() }.add(Weighted(rate, total))
            allocations += Allocation(obs.countId, 1, mapOf(set to obs.q))
        } else {
            val shares = present.mapValues { (_, days) ->
                obs.q * Rational.of(days.toLong()) / Rational.of(total.toLong())
            }
            for (set in present.keys) {
                scheme2.getOrPut(set) { mutableListOf() }.add(Weighted(rate, total))
            }
            allocations += Allocation(obs.countId, 2, shares)
        }
    }
}

private fun merge(parts: List<Weighted>?): Weighted? {
    if (parts.isNullOrEmpty()) return null
    val weight = parts.sumOf { it.weight }
    if (weight == 0) return null
    var acc = Rational.ZERO
    for (part in parts) acc += part.rate * Rational.of(part.weight.toLong())
    return Weighted(acc / Rational.of(weight.toLong()), weight)
}
