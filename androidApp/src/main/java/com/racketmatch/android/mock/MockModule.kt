package com.racketmatch.android.mock

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.AuthResult
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.ChatMessage
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.model.Court
import com.racketmatch.domain.model.EloPoint
import com.racketmatch.domain.model.Match
import com.racketmatch.domain.model.MatchStatus
import com.racketmatch.domain.model.MatchType
import com.racketmatch.domain.model.OpenSession
import com.racketmatch.domain.model.OpenSessionStatus
import com.racketmatch.domain.model.PaymentIntent
import com.racketmatch.domain.model.PlayerFilter
import com.racketmatch.domain.model.Sport
import com.racketmatch.domain.model.User
import com.racketmatch.domain.repository.AuthRepository
import com.racketmatch.domain.repository.ChatRepository
import com.racketmatch.domain.repository.CoachRepository
import com.racketmatch.domain.repository.CourtRepository
import com.racketmatch.domain.repository.MatchRepository
import com.racketmatch.domain.repository.OpenSessionRepository
import com.racketmatch.domain.repository.PaymentRepository
import com.racketmatch.domain.repository.PlayerRepository
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.koin.dsl.module

private const val MY_ID = "mock-user-1"

private val MOCK_USER = User(
    id = MY_ID,
    email = "jan@racketmatch.pl",
    displayName = "Jan Kowalski",
    avatarUrl = null,
    isCoach = false,
    city = "Warszawa",
    eloRating = 1450,
    isMaster = false,
    masterFee = null,
    subscriptionActive = true,
    sports = listOf(Sport.PADEL, Sport.TENNIS),
    eloPerSport = mapOf("PADEL" to 1450, "TENNIS" to 1380),
    bio = "Gram w padla od 3 lat, szukam równych sparingpartnerów w Warszawie.",
    wins = 18,
    losses = 12
)

private val MOCK_PLAYERS = listOf(
    User("p1", "anna@test.pl",  "Anna Nowak",       null, false, "Warszawa", 1520, true,  null, true,  listOf(Sport.TENNIS), mapOf("TENNIS" to 1520), bio = "Tenisistka z 8-letnim stażem. Lubię agresywny baseline.", wins = 34, losses = 11),
    User("p2", "piotr@test.pl", "Piotr Wiśniewski", null, false, "Warszawa", 1380, false, null, false, listOf(Sport.PADEL),  mapOf("PADEL" to 1380),  bio = null, wins = 7, losses = 14),
    User("p3", "maria@test.pl", "Maria Kowalczyk",  null, false, "Kraków",   1610, true,  null, true,  listOf(Sport.TENNIS, Sport.PADEL), mapOf("TENNIS" to 1610, "PADEL" to 1540), bio = "Finalistka Mistrzostw Małopolski. Trenuję 5x w tygodniu.", wins = 52, losses = 18),
    User("p4", "adam@test.pl",  "Adam Zając",       null, false, "Warszawa", 1290, false, null, false, listOf(Sport.PADEL),  mapOf("PADEL" to 1290),  bio = null, wins = 4, losses = 9),
    User("p5", "ewa@test.pl",   "Ewa Dąbrowska",    null, true,  "Wrocław",  1700, true,  null, true,  listOf(Sport.TENNIS), mapOf("TENNIS" to 1700), bio = "Profesjonalna zawodniczka. Coaching dostępny po wcześniejszym kontakcie.", wins = 71, losses = 22),
)

private val MOCK_MATCHES = mutableListOf(
    Match(
        id = "m1", challengerId = MY_ID, challengedId = "p1",
        challengerName = "Jan Kowalski", challengerElo = 1450,
        challengedName = "Anna Nowak", challengedElo = 1520,
        type = MatchType.RANKED, status = MatchStatus.SCHEDULED, sport = Sport.PADEL,
        scheduledAt = "2026-04-05T14:00:00Z",
        locationName = "Warsaw Padel Club"
    ),
    Match(
        id = "m2", challengerId = "p2", challengedId = MY_ID,
        challengerName = "Piotr Wiśniewski", challengerElo = 1380,
        challengedName = "Jan Kowalski", challengedElo = 1450,
        type = MatchType.CASUAL, status = MatchStatus.COMPLETED, sport = Sport.TENNIS,
        scheduledAt = "2026-03-20T10:00:00Z",
        eloChanges = mapOf(MY_ID to +25, "p2" to -25),
        scoreChallenger = 3, scoreChallenged = 6
    ),
    Match(
        id = "m3", challengerId = "p3", challengedId = MY_ID,
        challengerName = "Maria Kowalczyk", challengerElo = 1610,
        challengedName = "Jan Kowalski", challengedElo = 1450,
        type = MatchType.RANKED, status = MatchStatus.PENDING, sport = Sport.PADEL
    ),
    Match(
        id = "m4", challengerId = MY_ID, challengedId = "p4",
        challengerName = "Jan Kowalski", challengerElo = 1450,
        challengedName = "Adam Zając", challengedElo = 1290,
        type = MatchType.CASUAL, status = MatchStatus.PENDING, sport = Sport.PADEL
    ),
    Match(
        id = "m5", challengerId = MY_ID, challengedId = "p2",
        challengerName = "Jan Kowalski", challengerElo = 1450,
        challengedName = "Piotr Wiśniewski", challengedElo = 1380,
        type = MatchType.RANKED, status = MatchStatus.PENDING, sport = Sport.PADEL,
        locationName = "Kort Bema",
        scheduledAt = "2026-04-10T16:00:00Z",
        detailsProposedBy = "p2"
    ),
)

private val MOCK_COACHES = listOf(
    CoachProfile("c1", "Tomasz Malinowski", null, "Certyfikowany trener tenisa z 10-letnim doświadczeniem.", 15000, listOf(Sport.TENNIS), listOf("PTT Level 3"), "Warszawa", 1800),
    CoachProfile("c2", "Karolina Szymańska", null, "Specjalistka od padla, finalistka mistrzostw Polski.", 12000, listOf(Sport.PADEL, Sport.TENNIS), listOf("FIP Certified"), "Warszawa", 1750),
)

private val MOCK_SLOTS = listOf(
    BookingSlot(Instant.parse("2026-03-27T09:00:00Z"), Instant.parse("2026-03-27T10:00:00Z"), true),
    BookingSlot(Instant.parse("2026-03-27T11:00:00Z"), Instant.parse("2026-03-27T12:00:00Z"), true),
    BookingSlot(Instant.parse("2026-03-28T09:00:00Z"), Instant.parse("2026-03-28T10:00:00Z"), false),
)

private val MOCK_MESSAGES = listOf(
    ChatMessage("msg1", "m1", "p1",  "Hej, potwierdzamy mecz w piątek?", System.currentTimeMillis() - 3600_000),
    ChatMessage("msg2", "m1", MY_ID, "Tak, będę o 14:00 na korcie 3.",   System.currentTimeMillis() - 3000_000),
    ChatMessage("msg3", "m1", "p1",  "Super, do zobaczenia!",             System.currentTimeMillis() - 1800_000),
)

private val MOCK_ELO_HISTORY = listOf(
    EloPoint(System.currentTimeMillis() - 30 * 86400_000L, 1300),
    EloPoint(System.currentTimeMillis() - 20 * 86400_000L, 1350),
    EloPoint(System.currentTimeMillis() - 10 * 86400_000L, 1400),
    EloPoint(System.currentTimeMillis() -  5 * 86400_000L, 1425),
    EloPoint(System.currentTimeMillis(),                    1450),
)

private val MOCK_COURTS = listOf(
    Court("court-1", "Warsaw Padel Club",   "Warszawa", "ul. Wołoska 18, Mokotów",         52.1862, 21.0013, listOf(Sport.PADEL),                "https://playtomic.com/clubs/warsaw-padel-club"),
    Court("court-2", "Legia Tennis Club",   "Warszawa", "ul. Łazienkowska 3, Śródmieście", 52.2211, 21.0353, listOf(Sport.TENNIS),               "https://playtomic.com/clubs/legia-tennis-club"),
    Court("court-3", "Kort Bema",           "Warszawa", "ul. Bema 71, Wola",               52.2343, 20.9738, listOf(Sport.TENNIS, Sport.PADEL),  null),
    Court("court-4", "Korty Moczydło",      "Warszawa", "ul. Górczewska 8, Wola",          52.2317, 20.9829, listOf(Sport.TENNIS),               null),
    Court("court-5", "Kortowo Ursynów",     "Warszawa", "ul. Wąwozowa 14, Ursynów",        52.1478, 21.0614, listOf(Sport.TENNIS, Sport.PADEL),  "https://playtomic.com/clubs/kortowo-ursynow"),
    Court("court-6", "Padel Arena Wilanów", "Warszawa", "ul. Klimczaka 1, Wilanów",        52.1641, 21.0869, listOf(Sport.PADEL),                "https://playtomic.com/clubs/padel-arena-wilanow"),
    Court("court-7", "SmashPoint Padel",    "Warszawa", "ul. Obrzeżna 3, Mokotów",         52.1923, 21.0228, listOf(Sport.PADEL),                "https://playtomic.com/clubs/smashpoint-padel"),
)

private val now = Clock.System.now().toEpochMilliseconds()
private val MOCK_SESSIONS = mutableListOf(
    OpenSession("s1", "court-1", "Warsaw Padel Club", "p1", "Anna Nowak",       1520, null, now + 3 * 3600_000L,  Sport.PADEL,  OpenSessionStatus.OPEN),
    OpenSession("s2", "court-3", "Kort Bema",         "p2", "Piotr Wiśniewski", 1380, null, now + 5 * 3600_000L,  Sport.TENNIS, OpenSessionStatus.OPEN),
    OpenSession("s3", "court-3", "Kort Bema",         "p3", "Maria Kowalczyk",  1610, null, now + 26 * 3600_000L, Sport.PADEL,  OpenSessionStatus.OPEN),
)

private var sessionIdCounter = 10
private var matchIdCounter = 10

val mockRepositoryModule = module {

    single<AuthRepository> {
        val tokenStorage = get<TokenStorage>()
        object : AuthRepository {
            override suspend fun login(email: String, password: String): AuthResult {
                tokenStorage.saveTokens("mock-access-token", "mock-refresh-token")
                tokenStorage.currentUserId = MY_ID
                return AuthResult("mock-access-token", "mock-refresh-token", MOCK_USER)
            }
            override suspend fun register(email: String, password: String, displayName: String, city: String, isCoach: Boolean, sports: List<Sport>): AuthResult {
                tokenStorage.saveTokens("mock-access-token", "mock-refresh-token")
                tokenStorage.currentUserId = MY_ID
                val eloMap = sports.associate { it.name to 1200 }
                return AuthResult("mock-access-token", "mock-refresh-token", MOCK_USER.copy(displayName = displayName, city = city, isCoach = isCoach, sports = sports, eloPerSport = eloMap))
            }
            override suspend fun logout() { tokenStorage.clear() }
        }
    }

    single<PlayerRepository> {
        object : PlayerRepository {
            override suspend fun getNearbyPlayers(filter: PlayerFilter, lat: Double, lng: Double) = MOCK_PLAYERS
        }
    }

    single<MatchRepository> {
        val tokenStorage = get<TokenStorage>()
        object : MatchRepository {
            override suspend fun getMyMatches(): List<Match> = MOCK_MATCHES.toList()

            override suspend fun getMatch(matchId: String) = MOCK_MATCHES.first { it.id == matchId }

            override suspend fun sendChallenge(challengedId: String, type: MatchType, sport: Sport, locationName: String?, scheduledAt: Long?): Match {
                val player = MOCK_PLAYERS.find { it.id == challengedId }
                val newMatch = Match(
                    id = "m${matchIdCounter++}",
                    challengerId = MY_ID,
                    challengedId = challengedId,
                    challengerName = MOCK_USER.displayName,
                    challengerElo = MOCK_USER.eloRating,
                    challengedName = player?.displayName ?: "Opponent",
                    challengedElo = player?.eloRating ?: 1200,
                    type = type,
                    status = MatchStatus.PENDING,
                    sport = sport,
                    locationName = locationName,
                    scheduledAt = scheduledAt?.let { Instant.fromEpochMilliseconds(it).toString() },
                    detailsProposedBy = if (locationName != null || scheduledAt != null) MY_ID else null
                )
                MOCK_MATCHES.add(newMatch)
                tokenStorage.incrementMatchesVersion()
                return newMatch
            }

            override suspend fun acceptMatch(matchId: String): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(status = MatchStatus.SCHEDULED, detailsProposedBy = null)
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }

            override suspend fun proposeDetails(matchId: String, locationName: String?, scheduledAt: Long?): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(
                    locationName = locationName,
                    scheduledAt = scheduledAt?.let { Instant.fromEpochMilliseconds(it).toString() },
                    detailsProposedBy = MY_ID
                )
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }

            override suspend fun declineMatch(matchId: String): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(status = MatchStatus.CANCELLED)
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }

            override suspend fun submitResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val match = MOCK_MATCHES[idx]
                val challengerWon = scoreChallenger > scoreChallenged
                val eloChange = if (match.type == MatchType.RANKED) 20 else 0
                val updated = match.copy(
                    status = MatchStatus.COMPLETED,
                    scoreChallenger = scoreChallenger,
                    scoreChallenged = scoreChallenged,
                    eloChanges = if (match.type == MatchType.RANKED) mapOf(
                        match.challengerId to if (challengerWon) eloChange else -eloChange,
                        match.challengedId to if (!challengerWon) eloChange else -eloChange
                    ) else null
                )
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }

            override suspend fun proposeResult(matchId: String, scoreChallenger: Int, scoreChallenged: Int): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(
                    status = MatchStatus.RESULT_PROPOSED,
                    proposedScoreChallenger = scoreChallenger,
                    proposedScoreChallenged = scoreChallenged,
                    proposedBy = MY_ID
                )
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }

            override suspend fun confirmResult(matchId: String): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val match = MOCK_MATCHES[idx]
                val sc = match.proposedScoreChallenger ?: 0
                val sd = match.proposedScoreChallenged ?: 0
                val challengerWon = sc > sd
                val eloChange = if (match.type == MatchType.RANKED) 20 else 0
                val updated = match.copy(
                    status = MatchStatus.COMPLETED,
                    scoreChallenger = sc,
                    scoreChallenged = sd,
                    proposedScoreChallenger = null,
                    proposedScoreChallenged = null,
                    proposedBy = null,
                    eloChanges = if (match.type == MatchType.RANKED) mapOf(
                        match.challengerId to if (challengerWon) eloChange else -eloChange,
                        match.challengedId to if (!challengerWon) eloChange else -eloChange
                    ) else null
                )
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }

            override suspend fun disputeResult(matchId: String): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(
                    status = MatchStatus.SCHEDULED,
                    proposedScoreChallenger = null,
                    proposedScoreChallenged = null,
                    proposedBy = null
                )
                MOCK_MATCHES[idx] = updated
                return updated
            }

            override suspend fun claimReservation(matchId: String): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(reservedBy = MY_ID)
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }

            override suspend fun cancelChallenge(matchId: String): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(status = MatchStatus.CANCELLED)
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }
        }
    }

    single<ChatRepository> {
        object : ChatRepository {
            override suspend fun loadHistory(matchId: String) = MOCK_MESSAGES.filter { it.matchId == matchId }
            override suspend fun sendMessage(matchId: String, text: String) {}
            override fun observeMessages(matchId: String): Flow<ChatMessage> = emptyFlow()
        }
    }

    single<CoachRepository> {
        object : CoachRepository {
            override suspend fun getCoaches(city: String) = MOCK_COACHES
            override suspend fun getCoach(coachId: String) = MOCK_COACHES.first { it.userId == coachId }
            override suspend fun getAvailability(coachId: String, from: Instant, to: Instant) = MOCK_SLOTS
            override suspend fun bookSlot(coachId: String, startsAt: Instant, endsAt: Instant) {}
        }
    }

    single<PaymentRepository> {
        object : PaymentRepository {
            override suspend fun createSubscriptionIntent()                  = PaymentIntent("mock_secret_sub",   4900)
            override suspend fun createMasterMatchIntent(matchId: String)   = PaymentIntent("mock_secret_match", 2900)
        }
    }

    single<CourtRepository> {
        object : CourtRepository {
            override suspend fun getCourts(city: String) = MOCK_COURTS.filter { it.city == city }
        }
    }

    single<OpenSessionRepository> {
        val tokenStorage = get<TokenStorage>()
        object : OpenSessionRepository {
            override suspend fun getSessions(city: String) = MOCK_SESSIONS.filter { it.status == OpenSessionStatus.OPEN }

            override suspend fun postSession(courtId: String, sport: String, startsAtMillis: Long): OpenSession {
                val court = MOCK_COURTS.find { it.id == courtId }
                val session = OpenSession(
                    id = "s${sessionIdCounter++}",
                    courtId = courtId,
                    courtName = court?.name ?: courtId,
                    userId = MY_ID,
                    userName = MOCK_USER.displayName,
                    userElo = MOCK_USER.eloRating,
                    userAvatarUrl = null,
                    startsAt = startsAtMillis,
                    sport = runCatching { Sport.valueOf(sport) }.getOrDefault(Sport.TENNIS),
                    status = OpenSessionStatus.OPEN
                )
                MOCK_SESSIONS.add(session)
                tokenStorage.incrementMatchesVersion()
                return session
            }

            override suspend fun joinSession(sessionId: String): String {
                val idx = MOCK_SESSIONS.indexOfFirst { it.id == sessionId }
                if (idx >= 0) MOCK_SESSIONS[idx] = MOCK_SESSIONS[idx].copy(status = OpenSessionStatus.FILLED)
                val matchId = "m${matchIdCounter++}"
                tokenStorage.incrementMatchesVersion()
                return matchId
            }

            override suspend fun cancelSession(sessionId: String) {
                val idx = MOCK_SESSIONS.indexOfFirst { it.id == sessionId }
                if (idx >= 0) MOCK_SESSIONS[idx] = MOCK_SESSIONS[idx].copy(status = OpenSessionStatus.CANCELLED)
            }
        }
    }

    single<ProfileRepository> {
        var currentProfile = MOCK_USER
        object : ProfileRepository {
            override suspend fun getMyProfile()     = currentProfile
            override suspend fun getRecentMatches() = MOCK_MATCHES.toList()
            override suspend fun getEloHistory()    = MOCK_ELO_HISTORY
            override suspend fun updateProfile(displayName: String, city: String, bio: String?, sports: List<Sport>, password: String?): User {
                currentProfile = currentProfile.copy(
                    displayName = displayName,
                    city = city,
                    bio = bio,
                    sports = sports,
                    eloPerSport = sports.associate { it.name to (currentProfile.eloPerSport[it.name] ?: 1200) }
                )
                return currentProfile
            }
        }
    }
}
