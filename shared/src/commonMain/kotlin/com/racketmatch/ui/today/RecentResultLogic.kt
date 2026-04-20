package com.racketmatch.ui.today

import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus

/**
 * Decision packet returned by [computeRecentResultDecision] — "which match,
 * if any, to celebrate in the hero slot, and which ids to write to the
 * seen-set when the user acknowledges it (by tapping)".
 */
data class RecentResultDecision(
    /** Match id to show as hero, or null when there is nothing to celebrate. */
    val heroMatchId: String?,
    /**
     * Ids to write to the seen-set the moment the user taps the hero. Always
     * includes the tapped match, and also *every other* completed-unseen
     * match the user has. A single tap clears the whole backlog.
     *
     * Empty when [heroMatchId] is null.
     */
    val onTapMarkSeen: Set<String>,
)

/**
 * Picks the hero to show for "opponent just confirmed a result" and decides
 * which ids to clear in one shot.
 *
 * Why the backlog-clear matters: without it, a user with N completed-but-
 * unacknowledged matches sees N reveals one-after-another (tap → reveal → come
 * back → next match's hero pops up → tap → reveal → …). That's the
 * "jeden po drugim" bug. Seeing one reveal means the user has opened the
 * flow and understood where past results live — they don't need to tap
 * through every one individually. The tapped match is still the one that
 * gets revealed, but the others are silently marked seen so the hero
 * doesn't immediately rearm for the next in the backlog.
 *
 * Selection is by latest `scheduledAt` ISO-8601 string sort — naive string
 * compare works because the format is lexicographically orderable.
 */
fun computeRecentResultDecision(
    matches: List<Match>,
    myUserId: String,
    seenIds: Set<String>,
): RecentResultDecision {
    if (myUserId.isBlank()) return RecentResultDecision(null, emptySet())

    val candidates = matches.filter {
        it.status == MatchStatus.COMPLETED &&
            (it.challengerId == myUserId || it.challengedId == myUserId) &&
            it.eloChanges?.get(myUserId) != null &&
            it.id !in seenIds
    }
    if (candidates.isEmpty()) return RecentResultDecision(null, emptySet())

    val latest = candidates.maxByOrNull { it.scheduledAt ?: "" }!!
    return RecentResultDecision(
        heroMatchId = latest.id,
        onTapMarkSeen = candidates.map { it.id }.toSet(),
    )
}
