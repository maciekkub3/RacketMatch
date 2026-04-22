package com.racketmatch.domain.model

enum class Sport { TENNIS, PADEL }

/**
 * Human-facing emoji for a sport. Centralised so adding SQUASH / PING_PONG /
 * BADMINTON later is a single edit instead of touching every card.
 */
fun Sport.emoji(): String = when (this) {
    Sport.TENNIS -> "🎾"
    Sport.PADEL -> "🏸"
}

/** Polish display name (nominative, capitalised). */
fun Sport.label(): String = when (this) {
    Sport.TENNIS -> "Tenis"
    Sport.PADEL -> "Padel"
}

/** Genitive/accusative form used in copy like "grasz w {tenisa}". */
fun Sport.accusative(): String = when (this) {
    Sport.TENNIS -> "tenisa"
    Sport.PADEL -> "padla"
}
