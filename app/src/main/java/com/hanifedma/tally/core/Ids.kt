package com.hanifedma.tally.core

import java.security.MessageDigest
import java.util.UUID

/**
 * Row ids.
 *
 * Every id is made on the client. A transaction written on a phone with no
 * signal has its final identity from the moment Save is pressed, so replaying
 * it when the network returns cannot create a second copy of it.
 */
object Ids {

    fun random(): String = UUID.randomUUID().toString()

    /**
     * A UUID derived from a user id and a name — the same inputs always give
     * the same id, on any device, in either app.
     *
     * This is what makes seeding a brand-new account safe. A phone and a
     * laptop opening Tally for the first time in the same minute both try to
     * create "Food"; because both compute the same primary key, the second
     * write lands on the first row instead of beside it.
     *
     * RFC 4122 §4.3, name-based, with SHA-1 replaced by SHA-256 truncated to
     * 16 bytes. The version nibble still says 5, which is a small untruth
     * that keeps every UUID parser happy and costs nothing. Must match
     * derivedId in money.js byte for byte.
     */
    fun derived(userId: String, name: String): String {
        val data = ("tally:$userId:$name").toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        val b = digest.copyOf(16)
        b[6] = ((b[6].toInt() and 0x0f) or 0x50).toByte()
        b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = b.joinToString("") { "%02x".format(it) }
        return buildString {
            append(hex, 0, 8); append('-')
            append(hex, 8, 12); append('-')
            append(hex, 12, 16); append('-')
            append(hex, 16, 20); append('-')
            append(hex, 20, 32)
        }
    }
}

/**
 * What a new account starts with.
 *
 * Names are stored in whichever language was on screen at sign-up and are
 * editable afterwards — a category is the user's word for something, so
 * translating it under them later would be wrong. Identical to the seed lists
 * in money.js, including the slugs, because the derived ids are built from
 * them and both apps must arrive at the same rows.
 */
data class SeedCategory(
    val slug: String,
    val icon: String,
    val color: String,
    val kind: String,
    val en: String,
    val ko: String,
) {
    fun name(lang: String) = if (lang == "ko") ko else en
}

data class SeedAccount(
    val slug: String,
    val kind: String,
    val color: String,
    val en: String,
    val ko: String,
) {
    fun name(lang: String) = if (lang == "ko") ko else en
}

val SEED_CATEGORIES = listOf(
    SeedCategory("food", "🍜", "orange", "expense", "Food", "식비"),
    SeedCategory("transport", "🚌", "sky", "expense", "Transport", "교통"),
    SeedCategory("household", "🏠", "amber", "expense", "Household", "생활"),
    SeedCategory("groceries", "🛒", "lime", "expense", "Groceries", "장보기"),
    SeedCategory("social", "🥂", "pink", "expense", "Social", "모임"),
    SeedCategory("health", "🧘", "teal", "expense", "Health", "건강"),
    SeedCategory("shopping", "🛍️", "rose", "expense", "Shopping", "쇼핑"),
    SeedCategory("education", "📘", "blue", "expense", "Education", "교육"),
    SeedCategory("fun", "🎬", "purple", "expense", "Fun", "여가"),
    SeedCategory("fees", "🧾", "gray", "expense", "Fees", "수수료"),
    SeedCategory("other-expense", "•", "gray", "expense", "Other", "기타"),

    SeedCategory("salary", "💼", "green", "income", "Salary", "급여"),
    SeedCategory("bonus", "✨", "lime", "income", "Bonus", "상여"),
    SeedCategory("gift-in", "🎁", "pink", "income", "Gift", "선물"),
    SeedCategory("refund", "↩️", "teal", "income", "Refund", "환급"),
    SeedCategory("other-income", "•", "gray", "income", "Other", "기타"),
)

val SEED_ACCOUNTS = listOf(
    SeedAccount("cash", "cash", "green", "Cash", "현금"),
    SeedAccount("bank", "bank", "indigo", "Bank", "은행"),
)
