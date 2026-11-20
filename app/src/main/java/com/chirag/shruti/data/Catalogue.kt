package com.chirag.shruti.data

/** The two books this app ships with. Only metadata — the files are yours to add. */
object Catalogue {
    val books = listOf(
        Book(
            id = "ikigai",
            title = "Ikigai",
            subtitle = "The Japanese Secret to a Long and Happy Life",
            authors = "Héctor García and Francesc Miralles",
            narrator = "Naoko Mori",
            badge = "MOST POPULAR",
            rating = 4.5f,
            ratingsLabel = "8.7k ratings",
            lengthLabel = "3h 23m",
            description = "Why do so many people in Okinawa live past a hundred? Ikigai — roughly, " +
                "\"a reason to get up in the morning\" — is the Japanese idea the authors set out to " +
                "understand. Drawing on conversations with the island's centenarians, they look at " +
                "purpose, flow, resilience, gentle movement, eating well and staying close to a " +
                "community, and how each of these can be practised in an ordinary day.",
            art = CoverArt.IKIGAI,
            tint = 0xFF5B8DB8,
        ),
        Book(
            id = "seven-secrets-of-shiva",
            title = "7 Secrets of Shiva",
            subtitle = "",
            authors = "Devdutt Pattanaik",
            narrator = null,
            badge = null,
            rating = null,
            ratingsLabel = null,
            lengthLabel = null,
            description = "Mythologist Devdutt Pattanaik looks at Shiva — the ash-smeared ascetic of " +
                "Kailash, the cosmic dancer, the householder with Shakti — and decodes the symbols, " +
                "stories and rituals that surround him, and what they reveal about the Indian way of " +
                "looking at the mind, desire and detachment.",
            art = CoverArt.SHIVA,
            tint = 0xFF1F8A8C,
        ),
    )
}
