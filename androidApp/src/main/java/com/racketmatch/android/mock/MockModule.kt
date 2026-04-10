package com.racketmatch.android.mock

import com.racketmatch.data.remote.TokenStorage
import com.racketmatch.domain.model.AuthResult
import com.racketmatch.domain.model.FriendRequest
import com.racketmatch.domain.model.FriendRequestStatus
import com.racketmatch.domain.model.BookingSettings
import com.racketmatch.domain.model.BookingSlot
import com.racketmatch.domain.model.CalendarEvent
import com.racketmatch.domain.model.CalendarEventType
import com.racketmatch.domain.model.ChatMessage
import com.racketmatch.domain.model.CoachBooking
import com.racketmatch.domain.model.CoachException
import com.racketmatch.domain.model.CoachProfile
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.model.PricingType
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
import com.racketmatch.domain.model.Conversation
import com.racketmatch.domain.model.DirectMessage
import com.racketmatch.domain.model.FeedEvent
import com.racketmatch.domain.model.FeedEventType
import com.racketmatch.domain.repository.DmRepository
import com.racketmatch.domain.repository.FeedRepository
import com.racketmatch.domain.repository.FriendRepository
import com.racketmatch.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Clock
import kotlin.time.Instant
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

private val MOCK_FEED = listOf(
    FeedEvent(
        id = "fe1", type = FeedEventType.MATCH_WON,
        actorId = "p1", actorName = "Anna Nowak", actorAvatarUrl = null,
        payload = mapOf("opponentName" to "Piotr W.", "score" to "6:3", "sport" to "Tennis"),
        createdAt = System.currentTimeMillis() - 3_600_000
    ),
    FeedEvent(
        id = "fe2", type = FeedEventType.ELO_MILESTONE,
        actorId = "p3", actorName = "Maria Kowalczyk", actorAvatarUrl = null,
        payload = mapOf("threshold" to "1600", "sport" to "Tennis"),
        createdAt = System.currentTimeMillis() - 7_200_000
    ),
    FeedEvent(
        id = "fe3", type = FeedEventType.MATCH_LOST,
        actorId = "p1", actorName = "Anna Nowak", actorAvatarUrl = null,
        payload = mapOf("opponentName" to "Maria K.", "score" to "4:6", "sport" to "Padel"),
        createdAt = System.currentTimeMillis() - 86_400_000
    )
)

private val MOCK_FRIENDS = mutableListOf<User>()
private val MOCK_RECEIVED_REQUESTS = mutableListOf(
    FriendRequest(
        id = "fr1",
        fromUserId = "p3",
        toUserId = MY_ID,
        fromName = "Maria Kowalczyk",
        fromAvatarUrl = null,
        toName = "Jan Kowalski",
        status = FriendRequestStatus.PENDING
    )
)
private val MOCK_SENT_REQUESTS = mutableListOf<FriendRequest>()

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
    CoachProfile("c1", "Tomasz Malinowski", null, "Certyfikowany trener tenisa z 10-letnim doświadczeniem.", listOf(Sport.TENNIS), listOf("PTT Level 3"), "Warszawa", 1800, 15000),
    CoachProfile("c2", "Karolina Szymańska", null, "Specjalistka od padla, finalistka mistrzostw Polski.", listOf(Sport.PADEL, Sport.TENNIS), listOf("FIP Certified"), "Warszawa", 1750, 12000),
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
    OpenSession("s1", "court-1", "Warsaw Padel Club", "p1", "Anna Nowak",       1520, null, now + 3 * 3600_000L,  Sport.PADEL,  OpenSessionStatus.OPEN, MatchType.RANKED),
    OpenSession("s2", "court-3", "Kort Bema",         "p2", "Piotr Wiśniewski", 1380, null, now + 5 * 3600_000L,  Sport.TENNIS, OpenSessionStatus.OPEN, MatchType.CASUAL),
    OpenSession("s3", "court-3", "Kort Bema",         "p3", "Maria Kowalczyk",  1610, null, now + 26 * 3600_000L, Sport.PADEL,  OpenSessionStatus.OPEN, MatchType.RANKED),
)

private var sessionIdCounter = 10
private var matchIdCounter = 10

private val MOCK_DM_MESSAGES = mutableMapOf<String, MutableList<DirectMessage>>()

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

            override suspend fun acceptDetails(matchId: String): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(detailsProposedBy = null)
                MOCK_MATCHES[idx] = updated
                tokenStorage.incrementMatchesVersion()
                return updated
            }

            override suspend fun discardDetails(matchId: String): Match {
                val idx = MOCK_MATCHES.indexOfFirst { it.id == matchId }
                val updated = MOCK_MATCHES[idx].copy(
                    detailsProposedBy = null,
                    locationName = null,
                    scheduledAt = null
                )
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
            override suspend fun getMyServices(): List<CoachService> = emptyList()
            override suspend fun getCoachServices(coachId: String): List<CoachService> = emptyList()
            override suspend fun getAvailability(coachId: String, from: Instant, to: Instant) = MOCK_SLOTS
            override suspend fun createBooking(coachId: String, serviceId: String, startsAt: Instant, endsAt: Instant, durationMinutes: Int): CoachBooking =
                CoachBooking("mock", coachId, "me", serviceId, null, startsAt, endsAt, durationMinutes, "PENDING")
            override suspend fun createService(name: String, description: String?, pricingType: String, priceCents: Int): CoachService =
                CoachService("mock", "me", name, description, PricingType.valueOf(pricingType), priceCents, true)
            override suspend fun updateService(serviceId: String, name: String, description: String?, pricingType: String, priceCents: Int, isActive: Boolean): CoachService =
                CoachService(serviceId, "me", name, description, PricingType.valueOf(pricingType), priceCents, isActive)
            override suspend fun deleteService(serviceId: String) {}
            override suspend fun getCalendarEvents(from: Instant, to: Instant): List<CalendarEvent> = emptyList()
            override suspend fun createCalendarEvent(title: String?, notes: String?, eventType: String, startsAt: Instant, endsAt: Instant): CalendarEvent =
                CalendarEvent("mock", title, notes, CalendarEventType.valueOf(eventType), startsAt, endsAt, null)
            override suspend fun deleteCalendarEvent(eventId: String) {}
            override suspend fun getMyAvailability(): List<com.racketmatch.domain.model.CoachWeeklyAvailability> = emptyList()
            override suspend fun saveMyAvailability(items: List<com.racketmatch.domain.model.CoachWeeklyAvailability>) = items
            override suspend fun getMyCoachProfile(): CoachProfile = MOCK_COACHES.first()
            override suspend fun getMyBookingSettings(): BookingSettings = BookingSettings()
            override suspend fun updateBookingSettings(leadTimeHours: Int, horizonDays: Int, bufferMinutes: Int) {}
            override suspend fun getMyExceptions(): List<CoachException> = emptyList()
            override suspend fun createException(startsAt: Instant, endsAt: Instant, label: String?): CoachException =
                CoachException("mock", startsAt, endsAt, label)
            override suspend fun deleteException(id: String) {}
            override suspend fun getPendingBookings(): List<CoachBooking> = emptyList()
            override suspend fun getMyBookings(): List<CoachBooking> = emptyList()
            override suspend fun confirmBooking(bookingId: String): CoachBooking =
                CoachBooking(bookingId, "me", "player", null, null, Instant.DISTANT_PAST, Instant.DISTANT_PAST, null, "CONFIRMED")
            override suspend fun declineBooking(bookingId: String): CoachBooking =
                CoachBooking(bookingId, "me", "player", null, null, Instant.DISTANT_PAST, Instant.DISTANT_PAST, null, "DECLINED")
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

            override suspend fun postSession(courtId: String, sport: String, startsAtMillis: Long, matchType: String): OpenSession {
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
                    status = OpenSessionStatus.OPEN,
                    matchType = runCatching { MatchType.valueOf(matchType) }.getOrDefault(MatchType.CASUAL)
                )
                MOCK_SESSIONS.add(session)
                tokenStorage.incrementMatchesVersion()
                return session
            }

            override suspend fun joinSession(sessionId: String): String {
                val idx = MOCK_SESSIONS.indexOfFirst { it.id == sessionId }
                val session = MOCK_SESSIONS.getOrNull(idx)
                if (idx >= 0) MOCK_SESSIONS[idx] = MOCK_SESSIONS[idx].copy(status = OpenSessionStatus.FILLED)
                val matchId = "m${matchIdCounter++}"
                if (session != null) {
                    val scheduledAtIso = kotlin.time.Instant.fromEpochMilliseconds(session.startsAt).toString()
                    MOCK_MATCHES.add(
                        Match(
                            id = matchId,
                            challengerId = session.userId,
                            challengedId = MY_ID,
                            challengerName = session.userName,
                            challengerElo = session.userElo,
                            challengedName = MOCK_USER.displayName,
                            challengedElo = MOCK_USER.eloRating,
                            type = session.matchType,
                            sport = session.sport,
                            status = MatchStatus.SCHEDULED,
                            locationName = session.courtName,
                            scheduledAt = scheduledAtIso
                        )
                    )
                }
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
            override suspend fun updateProfile(displayName: String, city: String, bio: String?, sports: List<Sport>, password: String?, dateOfBirth: String?, avatarUrl: String?): User {
                currentProfile = currentProfile.copy(
                    displayName = displayName,
                    city = city,
                    bio = bio,
                    dateOfBirth = dateOfBirth ?: currentProfile.dateOfBirth,
                    avatarUrl = avatarUrl ?: currentProfile.avatarUrl,
                    sports = sports,
                    eloPerSport = sports.associate { it.name to (currentProfile.eloPerSport[it.name] ?: 1200) }
                )
                return currentProfile
            }
            override suspend fun uploadAvatar(imageBytes: ByteArray): String = ""
        }
    }

    single<FriendRepository> {
        object : FriendRepository {
            override suspend fun sendRequest(userId: String): FriendRequest {
                val req = FriendRequest(
                    id = "fr_${System.currentTimeMillis()}",
                    fromUserId = MY_ID,
                    toUserId = userId,
                    fromName = MOCK_USER.displayName,
                    fromAvatarUrl = null,
                    toName = MOCK_PLAYERS.find { it.id == userId }?.displayName ?: userId,
                    status = FriendRequestStatus.PENDING
                )
                MOCK_SENT_REQUESTS.add(req)
                return req
            }
            override suspend fun acceptRequest(id: String): FriendRequest {
                val req = MOCK_RECEIVED_REQUESTS.first { it.id == id }
                MOCK_RECEIVED_REQUESTS.removeAll { it.id == id }
                val player = MOCK_PLAYERS.first { it.id == req.fromUserId }
                MOCK_FRIENDS.add(player)
                return req.copy(status = FriendRequestStatus.ACCEPTED)
            }
            override suspend fun declineRequest(id: String): FriendRequest {
                val req = MOCK_RECEIVED_REQUESTS.first { it.id == id }
                MOCK_RECEIVED_REQUESTS.removeAll { it.id == id }
                return req.copy(status = FriendRequestStatus.DECLINED)
            }
            override suspend fun cancelRequest(id: String) {
                MOCK_SENT_REQUESTS.removeAll { it.id == id }
            }
            override suspend fun getFriends(): List<User> = MOCK_FRIENDS.toList()
            override suspend fun getReceivedRequests(): List<FriendRequest> = MOCK_RECEIVED_REQUESTS.toList()
            override suspend fun getSentRequests(): List<FriendRequest> = MOCK_SENT_REQUESTS.toList()
            override suspend fun removeFriend(userId: String) { MOCK_FRIENDS.removeAll { it.id == userId } }
        }
    }

    single<FeedRepository> {
        object : FeedRepository {
            override suspend fun getFeed(before: Long?) = MOCK_FEED
        }
    }

    single<DmRepository> {
        object : DmRepository {
            override suspend fun getConversations(): List<Conversation> =
                MOCK_FRIENDS.map { friend ->
                    val convId = minOf(MY_ID, friend.id) + "_" + maxOf(MY_ID, friend.id)
                    val msgs = MOCK_DM_MESSAGES[convId] ?: emptyList()
                    Conversation(
                        id = convId,
                        otherUserId = friend.id,
                        otherUserName = friend.displayName,
                        otherUserAvatarUrl = null,
                        lastMessage = msgs.lastOrNull()?.text ?: "",
                        lastMessageAt = msgs.lastOrNull()?.sentAt ?: 0L,
                        unreadCount = 0
                    )
                }
            override suspend fun getMessages(conversationId: String): List<DirectMessage> =
                MOCK_DM_MESSAGES.getOrPut(conversationId) { mutableListOf() }.toList()
            override suspend fun sendMessage(conversationId: String, text: String): DirectMessage {
                val msg = DirectMessage(
                    id = "dm_${System.currentTimeMillis()}",
                    conversationId = conversationId,
                    senderId = MY_ID,
                    text = text,
                    sentAt = System.currentTimeMillis()
                )
                MOCK_DM_MESSAGES.getOrPut(conversationId) { mutableListOf() }.add(msg)
                return msg
            }
            override suspend fun markRead(conversationId: String) {}
            override fun observeMessages(conversationId: String) = emptyFlow<DirectMessage>()
        }
    }
}
