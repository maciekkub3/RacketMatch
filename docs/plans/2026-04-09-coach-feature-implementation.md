# Coach Feature Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use flow-executing-plans to implement this plan task-by-task.

**Goal:** Build a full coach panel with service catalog, personal calendar, booking approval flow, and coach/player mode switching.

**Architecture:** New tables `coach_services` and `coach_calendar_events` replace the single `hourly_rate` field. Bookings link to a service and carry `duration_minutes`. A `coachModeActive` flag in `TokenStorage` drives which `TabNavigator` is rendered in `MainScreen`.

**Tech Stack:** Spring Boot 3 (backend), Kotlin Multiplatform + Compose Multiplatform (shared/UI), Ktor 2 (HTTP client), Koin (DI), Voyager (navigation), MockK + Turbine (tests).

**Design doc:** `docs/plans/2026-04-09-coach-feature-design.md`

---

## Task 1: V14 DB Migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__coach_services_and_calendar.sql`

**Step 1: Write migration**

```sql
-- Coach service catalog
CREATE TABLE coach_services (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID NOT NULL REFERENCES coach_profiles(user_id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    pricing_type VARCHAR(20) NOT NULL,
    price_cents INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Coach personal calendar
CREATE TABLE coach_calendar_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coach_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200),
    notes TEXT,
    event_type VARCHAR(20) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    booking_id UUID REFERENCES bookings(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coach_services_coach ON coach_services(coach_id);
CREATE INDEX idx_calendar_coach ON coach_calendar_events(coach_id);
CREATE INDEX idx_calendar_range ON coach_calendar_events(coach_id, starts_at, ends_at);

-- Extend bookings
ALTER TABLE bookings ADD COLUMN service_id UUID REFERENCES coach_services(id);
ALTER TABLE bookings ADD COLUMN duration_minutes INT;

-- Remove hourly_rate from coach_profiles (price now comes from services)
ALTER TABLE coach_profiles DROP COLUMN IF EXISTS hourly_rate;
```

**Step 2: Rebuild Docker backend to apply migration**

```bash
cd backend && docker compose build --no-cache && docker compose up -d --force-recreate
```

Expected: containers start, Flyway log shows `V14__coach_services_and_calendar` applied successfully.

**Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V14__coach_services_and_calendar.sql
git commit -m "feat: V14 migration — coach_services, coach_calendar_events, drop hourly_rate"
```

---

## Task 2: Backend — CoachServiceEntity + Repository

**Files:**
- Create: `backend/src/main/kotlin/com/racketmatch/domain/entity/CoachServiceEntity.kt`
- Create: `backend/src/main/kotlin/com/racketmatch/domain/repository/CoachServiceRepository.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/entity/CoachProfileEntity.kt` — remove `hourlyRate`

**Step 1: Update CoachProfileEntity — remove hourlyRate**

In `CoachProfileEntity.kt`, remove:
```kotlin
@Column(name = "hourly_rate", nullable = false)
var hourlyRate: Int,
```

**Step 2: Create CoachServiceEntity**

```kotlin
package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coach_services")
class CoachServiceEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id", nullable = false)
    val coach: CoachProfileEntity,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "pricing_type", nullable = false, length = 20)
    var pricingType: String,  // PER_HOUR, FIXED, PER_PERSON

    @Column(name = "price_cents", nullable = false)
    var priceCents: Int,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
)
```

**Step 3: Create CoachServiceRepository**

```kotlin
package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.CoachServiceEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CoachServiceRepository : JpaRepository<CoachServiceEntity, UUID> {
    fun findByCoachUserIdAndIsActiveTrue(coachId: UUID): List<CoachServiceEntity>
    fun findByCoachUserId(coachId: UUID): List<CoachServiceEntity>
}
```

**Step 4: Rebuild backend, verify no Hibernate validation errors**

```bash
cd backend && docker compose up -d --force-recreate
docker compose logs api | grep -E "ERROR|Started"
```

Expected: `Started RacketMatchApiApplication` with no errors.

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/domain/entity/CoachServiceEntity.kt
git add backend/src/main/kotlin/com/racketmatch/domain/repository/CoachServiceRepository.kt
git add backend/src/main/kotlin/com/racketmatch/domain/entity/CoachProfileEntity.kt
git commit -m "feat: CoachServiceEntity + repository, remove hourlyRate from CoachProfileEntity"
```

---

## Task 3: Backend — CoachCalendarEventEntity + Repository

**Files:**
- Create: `backend/src/main/kotlin/com/racketmatch/domain/entity/CoachCalendarEventEntity.kt`
- Create: `backend/src/main/kotlin/com/racketmatch/domain/repository/CoachCalendarEventRepository.kt`

**Step 1: Create CoachCalendarEventEntity**

```kotlin
package com.racketmatch.domain.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coach_calendar_events")
class CoachCalendarEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id", nullable = false)
    val coach: UserEntity,

    @Column(length = 200)
    var title: String? = null,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null,

    @Column(name = "event_type", nullable = false, length = 20)
    val eventType: String,  // BOOKING, EXTERNAL_CLIENT, BLOCKED

    @Column(name = "starts_at", nullable = false)
    val startsAt: Instant,

    @Column(name = "ends_at", nullable = false)
    val endsAt: Instant,

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    val booking: BookingEntity? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now()
)
```

**Step 2: Create CoachCalendarEventRepository**

```kotlin
package com.racketmatch.domain.repository

import com.racketmatch.domain.entity.CoachCalendarEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface CoachCalendarEventRepository : JpaRepository<CoachCalendarEventEntity, UUID> {

    @Query("""
        SELECT e FROM CoachCalendarEventEntity e
        WHERE e.coach.id = :coachId
        AND e.startsAt < :to AND e.endsAt > :from
        ORDER BY e.startsAt
    """)
    fun findInRange(coachId: UUID, from: Instant, to: Instant): List<CoachCalendarEventEntity>
}
```

**Step 3: Rebuild and verify**

```bash
cd backend && docker compose up -d --force-recreate
docker compose logs api | grep -E "ERROR|Started"
```

**Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/domain/entity/CoachCalendarEventEntity.kt
git add backend/src/main/kotlin/com/racketmatch/domain/repository/CoachCalendarEventRepository.kt
git commit -m "feat: CoachCalendarEventEntity + repository"
```

---

## Task 4: Backend — Coach Service DTOs + CoachServiceController

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt`
- Create: `backend/src/main/kotlin/com/racketmatch/api/controller/CoachServiceController.kt`

**Step 1: Add DTOs to CoachDto.kt**

Add to end of `CoachDto.kt`:

```kotlin
data class CoachServiceDto(
    val id: UUID,
    val coachId: UUID,
    val name: String,
    val description: String?,
    val pricingType: String,
    val priceCents: Int,
    val isActive: Boolean
)

data class CreateCoachServiceRequest(
    val name: String,
    val description: String? = null,
    val pricingType: String,  // PER_HOUR, FIXED, PER_PERSON
    val priceCents: Int
)

data class UpdateCoachServiceRequest(
    val name: String,
    val description: String? = null,
    val pricingType: String,
    val priceCents: Int,
    val isActive: Boolean
)

fun CoachServiceEntity.toDto() = CoachServiceDto(
    id = id!!,
    coachId = coach.userId!!,
    name = name,
    description = description,
    pricingType = pricingType,
    priceCents = priceCents,
    isActive = isActive
)
```

Also update `CoachProfileDto` — remove `hourlyRate`, add `lowestServicePriceCents`:

```kotlin
data class CoachProfileDto(
    val userId: UUID,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val sports: List<String>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int?   // null if no active services yet
)
```

Update `CoachProfileEntity.toDto()` accordingly:
```kotlin
fun CoachProfileEntity.toDto(services: List<CoachServiceEntity> = emptyList()) = CoachProfileDto(
    userId = userId!!,
    displayName = user.displayName,
    avatarUrl = user.avatarUrl,
    bio = bio,
    sports = sports.toList(),
    certifications = certifications.toList(),
    city = user.city,
    eloRating = user.eloRating,
    lowestServicePriceCents = services.filter { it.isActive }.minOfOrNull { it.priceCents }
)
```

**Step 2: Create CoachServiceController**

```kotlin
package com.racketmatch.api.controller

import com.racketmatch.api.dto.*
import com.racketmatch.domain.repository.CoachProfileRepository
import com.racketmatch.domain.repository.CoachServiceRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
class CoachServiceController(
    private val coachServiceRepository: CoachServiceRepository,
    private val coachProfileRepository: CoachProfileRepository
) {

    // Player view — public
    @GetMapping("/api/coaches/{id}/services")
    fun getCoachServices(@PathVariable id: UUID): List<CoachServiceDto> =
        coachServiceRepository.findByCoachUserIdAndIsActiveTrue(id).map { it.toDto() }

    // Coach-only endpoints
    @PostMapping("/api/coach/services")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createService(
        authentication: Authentication,
        @RequestBody request: CreateCoachServiceRequest
    ): CoachServiceDto {
        val coachId = UUID.fromString(authentication.name)
        val profile = coachProfileRepository.findById(coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach profile not found") }
        val entity = coachServiceRepository.save(
            com.racketmatch.domain.entity.CoachServiceEntity(
                coach = profile,
                name = request.name,
                description = request.description,
                pricingType = request.pricingType,
                priceCents = request.priceCents
            )
        )
        return entity.toDto()
    }

    @PutMapping("/api/coach/services/{id}")
    @Transactional
    fun updateService(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: UpdateCoachServiceRequest
    ): CoachServiceDto {
        val coachId = UUID.fromString(authentication.name)
        val service = coachServiceRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found") }
        if (service.coach.userId != coachId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your service")
        service.name = request.name
        service.description = request.description
        service.pricingType = request.pricingType
        service.priceCents = request.priceCents
        service.isActive = request.isActive
        return coachServiceRepository.save(service).toDto()
    }

    @DeleteMapping("/api/coach/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun deleteService(authentication: Authentication, @PathVariable id: UUID) {
        val coachId = UUID.fromString(authentication.name)
        val service = coachServiceRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found") }
        if (service.coach.userId != coachId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your service")
        service.isActive = false
        coachServiceRepository.save(service)
    }
}
```

**Step 3: Update CoachController.getCoaches to pass services for min price**

In `CoachController.kt`, inject `CoachServiceRepository` and update:

```kotlin
@GetMapping
fun getCoaches(@RequestParam city: String): List<CoachProfileDto> =
    coachProfileRepository.findByCity(city).map { profile ->
        val services = coachServiceRepository.findByCoachUserIdAndIsActiveTrue(profile.userId!!)
        profile.toDto(services)
    }

@GetMapping("/{id}")
fun getCoach(@PathVariable id: UUID): CoachProfileDto {
    val profile = coachProfileRepository.findById(id)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }
    val services = coachServiceRepository.findByCoachUserIdAndIsActiveTrue(id)
    return profile.toDto(services)
}
```

**Step 4: Rebuild backend**

```bash
cd backend && docker compose up -d --force-recreate
docker compose logs api | grep -E "ERROR|Started"
```

**Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt
git add backend/src/main/kotlin/com/racketmatch/api/controller/CoachServiceController.kt
git add backend/src/main/kotlin/com/racketmatch/api/controller/CoachController.kt
git commit -m "feat: coach services CRUD endpoints, update coach profile DTO with lowestServicePriceCents"
```

---

## Task 5: Backend — CoachCalendarController

**Files:**
- Create: `backend/src/main/kotlin/com/racketmatch/api/controller/CoachCalendarController.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt` — add calendar DTOs

**Step 1: Add calendar DTOs to CoachDto.kt**

```kotlin
data class CalendarEventDto(
    val id: UUID,
    val title: String?,
    val notes: String?,
    val eventType: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val bookingId: UUID?
)

data class CreateCalendarEventRequest(
    val title: String?,
    val notes: String? = null,
    val eventType: String,  // EXTERNAL_CLIENT or BLOCKED only (BOOKING is auto-created)
    val startsAt: Instant,
    val endsAt: Instant
)

fun CoachCalendarEventEntity.toDto() = CalendarEventDto(
    id = id!!,
    title = title,
    notes = notes,
    eventType = eventType,
    startsAt = startsAt,
    endsAt = endsAt,
    bookingId = booking?.id
)
```

**Step 2: Create CoachCalendarController**

```kotlin
package com.racketmatch.api.controller

import com.racketmatch.api.dto.*
import com.racketmatch.domain.entity.CoachCalendarEventEntity
import com.racketmatch.domain.repository.CoachCalendarEventRepository
import com.racketmatch.domain.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/coach/calendar")
class CoachCalendarController(
    private val calendarRepository: CoachCalendarEventRepository,
    private val userRepository: UserRepository
) {

    @GetMapping
    fun getEvents(
        authentication: Authentication,
        @RequestParam from: Instant,
        @RequestParam to: Instant
    ): List<CalendarEventDto> {
        val coachId = UUID.fromString(authentication.name)
        return calendarRepository.findInRange(coachId, from, to).map { it.toDto() }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createEvent(
        authentication: Authentication,
        @RequestBody request: CreateCalendarEventRequest
    ): CalendarEventDto {
        val coachId = UUID.fromString(authentication.name)
        if (request.eventType == "BOOKING")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "BOOKING events are created automatically")
        val coach = userRepository.findById(coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        return calendarRepository.save(
            CoachCalendarEventEntity(
                coach = coach,
                title = request.title,
                notes = request.notes,
                eventType = request.eventType,
                startsAt = request.startsAt,
                endsAt = request.endsAt
            )
        ).toDto()
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun deleteEvent(authentication: Authentication, @PathVariable id: UUID) {
        val coachId = UUID.fromString(authentication.name)
        val event = calendarRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found") }
        if (event.coach.id != coachId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not your event")
        if (event.eventType == "BOOKING")
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete BOOKING event directly — cancel the booking instead")
        calendarRepository.delete(event)
    }
}
```

**Step 3: Update CoachController.getAvailability to use calendar events**

Replace the existing availability logic in `CoachController.kt`:

```kotlin
@GetMapping("/{id}/availability")
fun getAvailability(
    @PathVariable id: UUID,
    @RequestParam from: Instant,
    @RequestParam to: Instant
): List<BookingSlotDto> {
    coachProfileRepository.findById(id)
        .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }

    val busyRanges = calendarRepository.findInRange(id, from, to)
        .map { it.startsAt to it.endsAt }

    val slots = mutableListOf<BookingSlotDto>()
    var cursor = from.truncatedTo(ChronoUnit.HOURS)
    while (cursor.isBefore(to)) {
        val end = cursor.plus(1, ChronoUnit.HOURS)
        val isBusy = busyRanges.any { (s, e) -> s < end && e > cursor }
        slots.add(BookingSlotDto(startsAt = cursor, endsAt = end, isAvailable = !isBusy))
        cursor = end
    }
    return slots
}
```

Inject `CoachCalendarEventRepository` into `CoachController`.

**Step 4: Rebuild and commit**

```bash
cd backend && docker compose up -d --force-recreate
docker compose logs api | grep -E "ERROR|Started"
```

```bash
git add backend/src/main/kotlin/com/racketmatch/api/controller/CoachCalendarController.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt
git add backend/src/main/kotlin/com/racketmatch/api/controller/CoachController.kt
git commit -m "feat: coach calendar CRUD, update availability to use calendar events"
```

---

## Task 6: Backend — Update Booking Flow (service_id, decline, pending list, confirm→calendar)

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/domain/entity/BookingEntity.kt`
- Modify: `backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt`

**Step 1: Update BookingEntity — add service + duration**

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "service_id")
var service: CoachServiceEntity? = null,

@Column(name = "duration_minutes")
var durationMinutes: Int? = null,
```

**Step 2: Update CreateBookingRequest DTO**

In `CoachDto.kt`, replace `CreateBookingRequest`:
```kotlin
data class CreateBookingRequest(
    val coachId: UUID,
    val serviceId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int
)
```

Update `BookingDto` to include serviceId and durationMinutes:
```kotlin
data class BookingDto(
    val id: UUID,
    val coachId: UUID,
    val playerId: UUID,
    val serviceId: UUID?,
    val serviceName: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int?,
    val status: String
)

fun BookingEntity.toDto() = BookingDto(
    id = id!!,
    coachId = coach.id!!,
    playerId = player.id!!,
    serviceId = service?.id,
    serviceName = service?.name,
    startsAt = startsAt,
    endsAt = endsAt,
    durationMinutes = durationMinutes,
    status = status
)
```

**Step 3: Update BookingController**

```kotlin
@RestController
@RequestMapping("/api/bookings")
class BookingController(
    private val bookingRepository: BookingRepository,
    private val userRepository: UserRepository,
    private val coachServiceRepository: CoachServiceRepository,
    private val calendarRepository: CoachCalendarEventRepository
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createBooking(authentication: Authentication, @RequestBody request: CreateBookingRequest): BookingDto {
        val playerId = UUID.fromString(authentication.name)
        val player = userRepository.findById(playerId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found") }
        val coach = userRepository.findById(request.coachId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Coach not found") }
        val service = coachServiceRepository.findById(request.serviceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Service not found") }
        return bookingRepository.save(
            BookingEntity(
                coach = coach,
                player = player,
                service = service,
                startsAt = request.startsAt,
                endsAt = request.endsAt,
                durationMinutes = request.durationMinutes
            )
        ).toDto()
    }

    @GetMapping("/me")
    fun getMyBookings(authentication: Authentication): List<BookingDto> {
        val userId = UUID.fromString(authentication.name)
        return bookingRepository.findByUserId(userId).map { it.toDto() }
    }

    @GetMapping("/coach/pending")
    fun getPendingBookings(authentication: Authentication): List<BookingDto> {
        val coachId = UUID.fromString(authentication.name)
        return bookingRepository.findByCoachIdAndStatus(coachId, "PENDING").map { it.toDto() }
    }

    @PutMapping("/{id}/confirm")
    @Transactional
    fun confirmBooking(authentication: Authentication, @PathVariable id: UUID): BookingDto {
        val booking = findBookingForCoach(id, authentication.name)
        if (booking.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Booking is not pending")
        booking.status = "CONFIRMED"
        val saved = bookingRepository.save(booking)
        // Auto-create calendar event
        calendarRepository.save(
            CoachCalendarEventEntity(
                coach = booking.coach,
                title = booking.service?.name,
                eventType = "BOOKING",
                startsAt = booking.startsAt,
                endsAt = booking.endsAt,
                booking = saved
            )
        )
        return saved.toDto()
    }

    @PutMapping("/{id}/decline")
    @Transactional
    fun declineBooking(authentication: Authentication, @PathVariable id: UUID): BookingDto {
        val booking = findBookingForCoach(id, authentication.name)
        if (booking.status != "PENDING")
            throw ResponseStatusException(HttpStatus.CONFLICT, "Booking is not pending")
        booking.status = "DECLINED"
        return bookingRepository.save(booking).toDto()
    }

    @DeleteMapping("/{id}/cancel")
    @Transactional
    fun cancelBooking(authentication: Authentication, @PathVariable id: UUID): BookingDto {
        val userId = UUID.fromString(authentication.name)
        val booking = bookingRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found") }
        if (booking.coach.id != userId && booking.player.id != userId)
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant")
        booking.status = "CANCELLED"
        return bookingRepository.save(booking).toDto()
    }

    private fun findBookingForCoach(bookingId: UUID, userId: String): BookingEntity {
        val booking = bookingRepository.findById(bookingId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found") }
        if (booking.coach.id != UUID.fromString(userId))
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the coach can perform this action")
        return booking
    }
}
```

**Step 4: Add `findByCoachIdAndStatus` to BookingRepository**

```kotlin
fun findByCoachIdAndStatus(coachId: UUID, status: String): List<BookingEntity>
```

**Step 5: Rebuild and commit**

```bash
cd backend && docker compose up -d --force-recreate
docker compose logs api | grep -E "ERROR|Started"
```

```bash
git add backend/src/main/kotlin/com/racketmatch/domain/entity/BookingEntity.kt
git add backend/src/main/kotlin/com/racketmatch/api/controller/BookingController.kt
git add backend/src/main/kotlin/com/racketmatch/api/dto/CoachDto.kt
git add backend/src/main/kotlin/com/racketmatch/domain/repository/BookingRepository.kt
git commit -m "feat: booking links to service, coach can decline, confirm auto-creates calendar event"
```

---

## Task 7: Backend — Auto-create CoachProfile on Registration

**Files:**
- Modify: `backend/src/main/kotlin/com/racketmatch/service/AuthService.kt`

**Step 1: Inject CoachProfileRepository into AuthService**

Add constructor param:
```kotlin
private val coachProfileRepository: CoachProfileRepository
```

**Step 2: After `userRepository.save(user)`, auto-create profile**

```kotlin
val user = userRepository.save(UserEntity(...))
if (isCoach) {
    coachProfileRepository.save(
        CoachProfileEntity(user = user)
    )
}
return buildAuthResponse(user)
```

Note: `CoachProfileEntity` constructor must now be valid without `hourlyRate` (already removed in Task 2). Ensure all fields have defaults.

**Step 3: Rebuild and commit**

```bash
cd backend && docker compose up -d --force-recreate
docker compose logs api | grep -E "ERROR|Started"
```

```bash
git add backend/src/main/kotlin/com/racketmatch/service/AuthService.kt
git commit -m "feat: auto-create coach_profile row on registration when isCoach=true"
```

---

## Task 8: KMP — Update Domain Models

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/model/CoachProfile.kt`

**Step 1: Update CoachProfile + add CoachService + CalendarEvent**

```kotlin
package com.racketmatch.domain.model

import kotlin.time.Instant

data class CoachProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val sports: List<Sport>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int?  // null if no active services
)

data class CoachService(
    val id: String,
    val coachId: String,
    val name: String,
    val description: String?,
    val pricingType: PricingType,
    val priceCents: Int,
    val isActive: Boolean
)

enum class PricingType { PER_HOUR, FIXED, PER_PERSON }

data class CalendarEvent(
    val id: String,
    val title: String?,
    val notes: String?,
    val eventType: CalendarEventType,
    val startsAt: Instant,
    val endsAt: Instant,
    val bookingId: String?
)

enum class CalendarEventType { BOOKING, EXTERNAL_CLIENT, BLOCKED }

data class CoachBooking(
    val id: String,
    val coachId: String,
    val playerId: String,
    val serviceId: String?,
    val serviceName: String?,
    val startsAt: Instant,
    val endsAt: Instant,
    val durationMinutes: Int?,
    val status: String
)

data class BookingSlot(
    val startsAt: Instant,
    val endsAt: Instant,
    val isAvailable: Boolean
)
```

**Step 2: Verify nothing broken**

```bash
./gradlew :shared:jvmTest
```

Expected: tests pass (or pre-existing failures only).

**Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/domain/model/CoachProfile.kt
git commit -m "feat: update coach domain models — CoachService, CalendarEvent, CoachBooking, remove hourlyRate"
```

---

## Task 9: KMP — Update CoachDto + Add New DTOs

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/CoachDto.kt`

**Step 1: Rewrite CoachDto.kt**

```kotlin
package com.racketmatch.data.remote.dto

import com.racketmatch.domain.model.*
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CoachProfileDto(
    val userId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val sports: List<String>,
    val certifications: List<String>,
    val city: String,
    val eloRating: Int,
    val lowestServicePriceCents: Int? = null
)

@Serializable
data class CoachServiceDto(
    val id: String,
    val coachId: String,
    val name: String,
    val description: String? = null,
    val pricingType: String,
    val priceCents: Int,
    val isActive: Boolean
)

@Serializable
data class CreateCoachServiceRequestDto(
    val name: String,
    val description: String? = null,
    val pricingType: String,
    val priceCents: Int
)

@Serializable
data class UpdateCoachServiceRequestDto(
    val name: String,
    val description: String? = null,
    val pricingType: String,
    val priceCents: Int,
    val isActive: Boolean
)

@Serializable
data class CalendarEventDto(
    val id: String,
    val title: String? = null,
    val notes: String? = null,
    val eventType: String,
    val startsAt: String,
    val endsAt: String,
    val bookingId: String? = null
)

@Serializable
data class CreateCalendarEventRequestDto(
    val title: String? = null,
    val notes: String? = null,
    val eventType: String,
    val startsAt: String,
    val endsAt: String
)

@Serializable
data class BookingSlotDto(
    val startsAt: String,
    val endsAt: String,
    val isAvailable: Boolean
)

@Serializable
data class CoachBookingDto(
    val id: String,
    val coachId: String,
    val playerId: String,
    val serviceId: String? = null,
    val serviceName: String? = null,
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int? = null,
    val status: String
)

@Serializable
data class CreateBookingRequestDto(
    val coachId: String,
    val serviceId: String,
    val startsAt: String,
    val endsAt: String,
    val durationMinutes: Int
)

// toDomain mappers
fun CoachProfileDto.toDomain() = CoachProfile(
    userId = userId,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio ?: "",
    sports = sports.map { Sport.valueOf(it) },
    certifications = certifications,
    city = city,
    eloRating = eloRating,
    lowestServicePriceCents = lowestServicePriceCents
)

fun CoachServiceDto.toDomain() = CoachService(
    id = id,
    coachId = coachId,
    name = name,
    description = description,
    pricingType = PricingType.valueOf(pricingType),
    priceCents = priceCents,
    isActive = isActive
)

fun CalendarEventDto.toDomain() = CalendarEvent(
    id = id,
    title = title,
    notes = notes,
    eventType = CalendarEventType.valueOf(eventType),
    startsAt = Instant.parse(startsAt),
    endsAt = Instant.parse(endsAt),
    bookingId = bookingId
)

fun BookingSlotDto.toDomain() = BookingSlot(
    startsAt = Instant.parse(startsAt),
    endsAt = Instant.parse(endsAt),
    isAvailable = isAvailable
)

fun CoachBookingDto.toDomain() = CoachBooking(
    id = id,
    coachId = coachId,
    playerId = playerId,
    serviceId = serviceId,
    serviceName = serviceName,
    startsAt = Instant.parse(startsAt),
    endsAt = Instant.parse(endsAt),
    durationMinutes = durationMinutes,
    status = status
)
```

**Step 2: Fix any compilation errors from callers of old `CoachProfileDto.hourlyRate`**

Search and fix: `CoachDetailScreen.kt` uses `coach.hourlyRate` — replace with display based on `lowestServicePriceCents`.

**Step 3: Run tests**

```bash
./gradlew :shared:jvmTest
```

**Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/dto/CoachDto.kt
git commit -m "feat: update KMP coach DTOs — services, calendar events, bookings"
```

---

## Task 10: KMP — Update CoachApi + CoachRepository

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/CoachApi.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/domain/repository/CoachRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/repository/CoachRepositoryImpl.kt`

**Step 1: Rewrite CoachApi.kt**

```kotlin
package com.racketmatch.data.remote.api

import com.racketmatch.data.remote.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlin.time.Instant

class CoachApi(private val client: HttpClient) {

    // Player-facing
    suspend fun getCoaches(city: String): List<CoachProfileDto> =
        client.get("api/coaches") { parameter("city", city) }.body()

    suspend fun getCoach(coachId: String): CoachProfileDto =
        client.get("api/coaches/$coachId").body()

    suspend fun getCoachServices(coachId: String): List<CoachServiceDto> =
        client.get("api/coaches/$coachId/services").body()

    suspend fun getAvailability(coachId: String, from: Instant, to: Instant): List<BookingSlotDto> =
        client.get("api/coaches/$coachId/availability") {
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.body()

    suspend fun createBooking(request: CreateBookingRequestDto): CoachBookingDto =
        client.post("api/bookings") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    // Coach-facing — services
    suspend fun createService(request: CreateCoachServiceRequestDto): CoachServiceDto =
        client.post("api/coach/services") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun updateService(serviceId: String, request: UpdateCoachServiceRequestDto): CoachServiceDto =
        client.put("api/coach/services/$serviceId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteService(serviceId: String) {
        client.delete("api/coach/services/$serviceId")
    }

    // Coach-facing — calendar
    suspend fun getCalendarEvents(from: Instant, to: Instant): List<CalendarEventDto> =
        client.get("api/coach/calendar") {
            parameter("from", from.toString())
            parameter("to", to.toString())
        }.body()

    suspend fun createCalendarEvent(request: CreateCalendarEventRequestDto): CalendarEventDto =
        client.post("api/coach/calendar") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun deleteCalendarEvent(eventId: String) {
        client.delete("api/coach/calendar/$eventId")
    }

    // Coach-facing — bookings
    suspend fun getPendingBookings(): List<CoachBookingDto> =
        client.get("api/bookings/coach/pending").body()

    suspend fun getMyBookings(): List<CoachBookingDto> =
        client.get("api/bookings/me").body()

    suspend fun confirmBooking(bookingId: String): CoachBookingDto =
        client.put("api/bookings/$bookingId/confirm").body()

    suspend fun declineBooking(bookingId: String): CoachBookingDto =
        client.put("api/bookings/$bookingId/decline").body()
}
```

**Step 2: Update CoachRepository interface**

```kotlin
interface CoachRepository {
    // Player-facing
    suspend fun getCoaches(city: String): List<CoachProfile>
    suspend fun getCoach(coachId: String): CoachProfile
    suspend fun getCoachServices(coachId: String): List<CoachService>
    suspend fun getAvailability(coachId: String, from: Instant, to: Instant): List<BookingSlot>
    suspend fun createBooking(coachId: String, serviceId: String, startsAt: Instant, endsAt: Instant, durationMinutes: Int): CoachBooking

    // Coach-facing — services
    suspend fun createService(name: String, description: String?, pricingType: String, priceCents: Int): CoachService
    suspend fun updateService(serviceId: String, name: String, description: String?, pricingType: String, priceCents: Int, isActive: Boolean): CoachService
    suspend fun deleteService(serviceId: String)

    // Coach-facing — calendar
    suspend fun getCalendarEvents(from: Instant, to: Instant): List<CalendarEvent>
    suspend fun createCalendarEvent(title: String?, notes: String?, eventType: String, startsAt: Instant, endsAt: Instant): CalendarEvent
    suspend fun deleteCalendarEvent(eventId: String)

    // Coach-facing — bookings
    suspend fun getPendingBookings(): List<CoachBooking>
    suspend fun getMyBookings(): List<CoachBooking>
    suspend fun confirmBooking(bookingId: String): CoachBooking
    suspend fun declineBooking(bookingId: String): CoachBooking
}
```

**Step 3: Implement CoachRepositoryImpl**

Implement all methods by delegating to `CoachApi` and calling `.toDomain()`.

**Step 4: Run tests**

```bash
./gradlew :shared:jvmTest
```

**Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/api/CoachApi.kt
git add shared/src/commonMain/kotlin/com/racketmatch/domain/repository/CoachRepository.kt
git add shared/src/commonMain/kotlin/com/racketmatch/data/repository/CoachRepositoryImpl.kt
git commit -m "feat: update CoachApi + CoachRepository with services, calendar, booking methods"
```

---

## Task 11: KMP — TokenStorage: isCoach + coachModeActive

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt`
- Modify: `androidApp/src/main/java/com/racketmatch/android/AndroidTokenStorage.kt`
- Modify: `shared/src/iosMain/kotlin/com/racketmatch/IosTokenStorage.kt`

**Step 1: Add to TokenStorage interface**

```kotlin
var isCoach: Boolean
var coachModeActive: Boolean
```

**Step 2: Add to InMemoryTokenStorage**

```kotlin
override var isCoach: Boolean = false
override var coachModeActive: Boolean = false
```

**Step 3: Add to AndroidTokenStorage (SharedPreferences)**

```kotlin
override var isCoach: Boolean
    get() = prefs.getBoolean("is_coach", false)
    set(value) { prefs.edit().putBoolean("is_coach", value).apply() }

override var coachModeActive: Boolean
    get() = prefs.getBoolean("coach_mode_active", false)
    set(value) { prefs.edit().putBoolean("coach_mode_active", value).apply() }
```

**Step 4: Add to IosTokenStorage (NSUserDefaults)**

```kotlin
override var isCoach: Boolean
    get() = NSUserDefaults.standardUserDefaults.boolForKey("is_coach")
    set(value) { NSUserDefaults.standardUserDefaults.setBool(value, forKey = "is_coach") }

override var coachModeActive: Boolean
    get() = NSUserDefaults.standardUserDefaults.boolForKey("coach_mode_active")
    set(value) { NSUserDefaults.standardUserDefaults.setBool(value, forKey = "coach_mode_active") }
```

**Step 5: Save isCoach after login/register in AuthRepositoryImpl**

In `AuthRepositoryImpl`, after saving tokens, also set:
```kotlin
tokenStorage.isCoach = result.user.isCoach
if (result.user.isCoach) tokenStorage.coachModeActive = true
```

**Step 6: Run tests**

```bash
./gradlew :shared:jvmTest
```

**Step 7: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/data/remote/TokenStorage.kt
git add androidApp/src/main/java/com/racketmatch/android/AndroidTokenStorage.kt
git add shared/src/iosMain/kotlin/com/racketmatch/IosTokenStorage.kt
git commit -m "feat: add isCoach + coachModeActive to TokenStorage"
```

---

## Task 12: KMP — New Coach ViewModels

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachServicesViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachCalendarViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachBookingsViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachProfileEditViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/CoachDetailViewModel.kt`
- Create tests for each ViewModel

**Step 1: Write failing tests first**

`shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/CoachServicesViewModelTest.kt`:

```kotlin
@ExtendWith(MockKExtension::class)
internal class CoachServicesViewModelTest {

    @MockK lateinit var repository: CoachRepository
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: CoachServicesViewModel

    @BeforeEach
    fun setUp() {
        viewModel = CoachServicesViewModel(repository, testDispatcher)
    }

    @Test
    fun `initial state is Loading`() = runTest {
        viewModel.stateFlow.value shouldBe CoachServicesState.Loading
    }

    @Test
    fun `loads services on init`() = runTest {
        val services = listOf(CoachService("1", "c1", "Trening", null, PricingType.PER_HOUR, 15000, true))
        coEvery { repository.createService(any(), any(), any(), any()) } returns services[0]
        coEvery { repository.getCoachServices(any()) } returns services

        viewModel.stateFlow.test {
            skipItems(1)
            testDispatcher.scheduler.advanceUntilIdle()
            // after explicit load trigger
        }
    }

    @Test
    fun `AddService event creates service and reloads`() = runTest {
        val service = CoachService("1", "c1", "Trening", null, PricingType.PER_HOUR, 15000, true)
        coEvery { repository.createService("Trening", null, "PER_HOUR", 15000) } returns service
        coEvery { repository.getCoachServices(any()) } returns listOf(service)

        viewModel.stateFlow.test {
            skipItems(1)
            viewModel.onEvent(CoachServicesEvent.AddService("Trening", null, "PER_HOUR", 15000))
            testDispatcher.scheduler.advanceUntilIdle()
            val state = awaitItem()
            state shouldBe CoachServicesState.Content(listOf(service))
        }
    }
}
```

**Step 2: Run to verify they fail**

```bash
./gradlew :shared:jvmTest --tests "*.CoachServicesViewModelTest"
```

Expected: FAIL (class doesn't exist).

**Step 3: Implement CoachServicesViewModel**

```kotlin
package com.racketmatch.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.racketmatch.domain.model.CoachService
import com.racketmatch.domain.repository.CoachRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CoachServicesState {
    object Loading : CoachServicesState()
    data class Content(val services: List<CoachService>) : CoachServicesState()
    object Error : CoachServicesState()
}

sealed class CoachServicesEvent {
    data class AddService(
        val name: String,
        val description: String?,
        val pricingType: String,
        val priceCents: Int
    ) : CoachServicesEvent()
    data class DeactivateService(val serviceId: String) : CoachServicesEvent()
    object Refresh : CoachServicesEvent()
}

class CoachServicesViewModel(
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<CoachServicesState>(CoachServicesState.Loading)
    val stateFlow = _state.asStateFlow()

    init { load() }

    fun onEvent(event: CoachServicesEvent) {
        when (event) {
            is CoachServicesEvent.AddService -> addService(event)
            is CoachServicesEvent.DeactivateService -> deactivate(event.serviceId)
            CoachServicesEvent.Refresh -> load()
        }
    }

    private fun load() {
        viewModelScope.launch(dispatcher) {
            _state.value = CoachServicesState.Loading
            try {
                // Coach sees their own services — use empty string, backend resolves from auth token
                val services = coachRepository.getCoachServices("")
                _state.value = CoachServicesState.Content(services)
            } catch (e: Exception) {
                _state.value = CoachServicesState.Error
            }
        }
    }

    private fun addService(event: CoachServicesEvent.AddService) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.createService(event.name, event.description, event.pricingType, event.priceCents)
                load()
            } catch (e: Exception) {
                _state.value = CoachServicesState.Error
            }
        }
    }

    private fun deactivate(serviceId: String) {
        viewModelScope.launch(dispatcher) {
            try {
                coachRepository.deleteService(serviceId)
                load()
            } catch (e: Exception) {
                _state.value = CoachServicesState.Error
            }
        }
    }
}
```

**Step 4: Implement CoachCalendarViewModel**

```kotlin
sealed class CoachCalendarState {
    object Loading : CoachCalendarState()
    data class Content(val events: List<CalendarEvent>, val weekStart: Instant) : CoachCalendarState()
    object Error : CoachCalendarState()
}

sealed class CoachCalendarEvent {
    data class LoadWeek(val weekStart: Instant) : CoachCalendarEvent()
    data class AddEvent(val title: String?, val notes: String?, val eventType: String, val startsAt: Instant, val endsAt: Instant) : CoachCalendarEvent()
    data class DeleteEvent(val eventId: String) : CoachCalendarEvent()
}

class CoachCalendarViewModel(
    private val coachRepository: CoachRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    // State + event handling — load 7-day window from weekStart
    // init: load current week (Clock.System.now() truncated to start of week)
}
```

**Step 5: Implement CoachBookingsViewModel**

```kotlin
sealed class CoachBookingsState {
    object Loading : CoachBookingsState()
    data class Content(val pending: List<CoachBooking>, val history: List<CoachBooking>) : CoachBookingsState()
    object Error : CoachBookingsState()
}

sealed class CoachBookingsEvent {
    data class Confirm(val bookingId: String) : CoachBookingsEvent()
    data class Decline(val bookingId: String) : CoachBookingsEvent()
    object Refresh : CoachBookingsEvent()
}

// Loads getPendingBookings() + getMyBookings(), splits into pending vs confirmed/declined/cancelled
```

**Step 6: Implement CoachProfileEditViewModel**

```kotlin
// Wraps existing ProfileRepository.updateProfile (bio, certifications via coach endpoint)
// Avatar upload delegates to ProfileApi.uploadAvatar() (already implemented)
```

**Step 7: Update CoachDetailViewModel — service selection flow**

Add `CoachDetailState.Content.services: List<CoachService>` field. Load services alongside coach profile. Update `BookSlot` event to take `serviceId + durationMinutes`.

**Step 8: Register new ViewModels in Koin**

In `NetworkModule.kt`, add to `viewModelModule`:
```kotlin
factory { CoachServicesViewModel(get()) }
factory { CoachCalendarViewModel(get()) }
factory { CoachBookingsViewModel(get()) }
factory { CoachProfileEditViewModel(get(), get()) }
```

**Step 9: Run tests**

```bash
./gradlew :shared:jvmTest
```

**Step 10: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/presentation/viewmodel/
git add shared/src/commonTest/kotlin/com/racketmatch/presentation/viewmodel/
git add shared/src/commonMain/kotlin/com/racketmatch/di/NetworkModule.kt
git commit -m "feat: CoachServicesViewModel, CoachCalendarViewModel, CoachBookingsViewModel, CoachProfileEditViewModel"
```

---

## Task 13: KMP — CoachTabNavigator + MainScreen Mode Switch

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt`

**Step 1: Add CoachTabNavigator and 4 coach tab stubs**

Add to `MainScreen.kt`:

```kotlin
// ─── Coach Tabs ───────────────────────────────────────────────────────────────

object CoachCalendarTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Kalendarz", icon = rememberVectorPainter(Icons.Default.DateRange))
    @Composable
    override fun Content() { Navigator(CoachCalendarScreen) { CurrentScreen() } }
}

object CoachBookingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 1u, title = "Rezerwacje", icon = rememberVectorPainter(Icons.Default.List))
    @Composable
    override fun Content() { Navigator(CoachBookingsScreen) { CurrentScreen() } }
}

object CoachServicesTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 2u, title = "Usługi", icon = rememberVectorPainter(Icons.Default.Star))
    @Composable
    override fun Content() { Navigator(CoachServicesScreen) { CurrentScreen() } }
}

object CoachProfileTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Profil", icon = rememberVectorPainter(Icons.Default.Person))
    @Composable
    override fun Content() { Navigator(CoachProfileEditScreen) { CurrentScreen() } }
}
```

**Step 2: Update MainScreen.Content to route based on coachModeActive**

```kotlin
val isCoach = tokenStorage.isCoach
var coachModeActive by remember { mutableStateOf(tokenStorage.coachModeActive) }

if (isCoach && coachModeActive) {
    CoachTabNavigatorContent(
        onSwitchToPlayer = {
            tokenStorage.coachModeActive = false
            coachModeActive = false
        }
    )
} else {
    PlayerTabNavigatorContent(  // existing TabNavigator wrapped in a function
        isCoach = isCoach,
        onSwitchToCoach = {
            tokenStorage.coachModeActive = true
            coachModeActive = true
        }
    )
}
```

**Step 3: Add mode switch button to MainTopBar**

When `isCoach=true`, show a small toggle button in the top bar (right side, next to bell icon):

```kotlin
if (isCoach) {
    IconButton(onClick = onModeSwitch) {
        Icon(
            painter = rememberVectorPainter(Icons.Default.SwitchAccount),
            contentDescription = if (coachModeActive) "Przełącz na gracza" else "Przełącz na trenera",
            tint = if (coachModeActive) ProCircuit.Lime else ProCircuit.OnBg
        )
    }
}
```

**Step 4: Build and run on device/emulator to verify routing**

**Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/navigation/MainScreen.kt
git commit -m "feat: CoachTabNavigator with 4 tabs, mode switch in top bar"
```

---

## Task 14: UI — CoachCalendarScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachCalendarScreen.kt`

**Step 1: Implement CoachCalendarScreen**

```kotlin
package com.racketmatch.ui.coaches

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.racketmatch.domain.model.CalendarEvent
import com.racketmatch.domain.model.CalendarEventType
import com.racketmatch.presentation.viewmodel.CoachCalendarEvent
import com.racketmatch.presentation.viewmodel.CoachCalendarState
import com.racketmatch.presentation.viewmodel.CoachCalendarViewModel
import com.racketmatch.ui.theme.AppBodyFontFamily
import com.racketmatch.ui.theme.AppFontFamily
import com.racketmatch.ui.theme.ProCircuit
import com.racketmatch.util.kmpViewModel
import kotlin.time.Instant

object CoachCalendarScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: CoachCalendarViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        var showAddSheet by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { viewModel.onEvent(CoachCalendarEvent.LoadWeek(/* current week start */)) }

        Scaffold(
            containerColor = ProCircuit.Bg,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    containerColor = ProCircuit.Lime,
                    contentColor = ProCircuit.Bg
                ) { Icon(Icons.Default.Add, contentDescription = "Dodaj zdarzenie") }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Text(
                    "Kalendarz",
                    fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 28.sp, color = ProCircuit.OnBg,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )

                when (val s = state) {
                    CoachCalendarState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    CoachCalendarState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Błąd ładowania kalendarza", color = ProCircuit.OnSurface)
                    }
                    is CoachCalendarState.Content -> {
                        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                            items(s.events) { event ->
                                CalendarEventCard(
                                    event = event,
                                    onDelete = if (event.eventType != CalendarEventType.BOOKING) ({
                                        viewModel.onEvent(CoachCalendarEvent.DeleteEvent(event.id))
                                    }) else null
                                )
                            }
                            if (s.events.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Text("Brak zdarzeń w tym tygodniu", color = ProCircuit.OnSurface,
                                            fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddSheet) {
            AddCalendarEventSheet(
                onDismiss = { showAddSheet = false },
                onConfirm = { title, notes, type, start, end ->
                    viewModel.onEvent(CoachCalendarEvent.AddEvent(title, notes, type, start, end))
                    showAddSheet = false
                }
            )
        }
    }
}

@Composable
private fun CalendarEventCard(event: CalendarEvent, onDelete: (() -> Unit)?) {
    val color = when (event.eventType) {
        CalendarEventType.BOOKING -> ProCircuit.Lime
        CalendarEventType.EXTERNAL_CLIENT -> Color(0xFF4A9EFF)
        CalendarEventType.BLOCKED -> ProCircuit.OnSurface
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(4.dp, 40.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(event.title ?: event.eventType.name, fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ProCircuit.OnBg)
            Text(
                "${event.startsAt.toString().substring(11, 16)} – ${event.endsAt.toString().substring(11, 16)}",
                fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface
            )
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) {
                Text("Usuń", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, color = ProCircuit.OnSurface)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCalendarEventSheet(
    onDismiss: () -> Unit,
    onConfirm: (String?, String?, String, Instant, Instant) -> Unit
) {
    // Bottom sheet with: title text field, event type selector (EXTERNAL_CLIENT/BLOCKED),
    // date+time pickers for start and end, notes field, confirm button
    // Implementation: use ModalBottomSheet + Material3 date/time pickers
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ProCircuit.SurfaceLow) {
        // Form fields — keep it simple for MVP
        Text("Dodaj zdarzenie", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
            fontSize = 18.sp, color = ProCircuit.OnBg,
            modifier = Modifier.padding(24.dp))
        // ... form fields
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachCalendarScreen.kt
git commit -m "feat: CoachCalendarScreen — weekly view with event cards and add sheet"
```

---

## Task 15: UI — CoachBookingsScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachBookingsScreen.kt`

**Step 1: Implement CoachBookingsScreen**

```kotlin
object CoachBookingsScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: CoachBookingsViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()

        LaunchedEffect(Unit) { viewModel.onEvent(CoachBookingsEvent.Refresh) }

        Column(modifier = Modifier.fillMaxSize().background(ProCircuit.Bg)) {
            Text("Rezerwacje", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 28.sp, color = ProCircuit.OnBg,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp))

            when (val s = state) {
                CoachBookingsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ProCircuit.Lime)
                }
                CoachBookingsState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Błąd ładowania rezerwacji", color = ProCircuit.OnSurface)
                }
                is CoachBookingsState.Content -> {
                    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                        if (s.pending.isNotEmpty()) {
                            item { SectionHeader("OCZEKUJĄCE") }
                            items(s.pending) { booking ->
                                BookingCard(
                                    booking = booking,
                                    onConfirm = { viewModel.onEvent(CoachBookingsEvent.Confirm(booking.id)) },
                                    onDecline = { viewModel.onEvent(CoachBookingsEvent.Decline(booking.id)) }
                                )
                            }
                        }
                        if (s.history.isNotEmpty()) {
                            item { SectionHeader("HISTORIA") }
                            items(s.history) { booking ->
                                BookingCard(booking = booking, onConfirm = null, onDecline = null)
                            }
                        }
                        if (s.pending.isEmpty() && s.history.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("Brak rezerwacji", color = ProCircuit.OnSurface,
                                        fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingCard(booking: CoachBooking, onConfirm: (() -> Unit)?, onDecline: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow).padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(booking.serviceName ?: "Rezerwacja", fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ProCircuit.OnBg)
                Text(booking.startsAt.toString().take(16).replace("T", " "),
                    fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
                booking.durationMinutes?.let {
                    Text("$it min", fontFamily = AppBodyFontFamily, fontSize = 12.sp, color = ProCircuit.OnSurface)
                }
            }
            StatusChip(booking.status)
        }
        if (onConfirm != null && onDecline != null) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg),
                    modifier = Modifier.weight(1f)) {
                    Text("AKCEPTUJ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                OutlinedButton(onClick = onDecline, shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)) {
                    Text("ODRZUĆ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachBookingsScreen.kt
git commit -m "feat: CoachBookingsScreen — pending + history with confirm/decline buttons"
```

---

## Task 16: UI — CoachServicesScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachServicesScreen.kt`

**Step 1: Implement CoachServicesScreen**

```kotlin
object CoachServicesScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: CoachServicesViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()
        var showAddSheet by remember { mutableStateOf(false) }

        Scaffold(
            containerColor = ProCircuit.Bg,
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddSheet = true },
                    containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj usługę")
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                Text("Moje usługi", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 28.sp, color = ProCircuit.OnBg,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp))

                when (val s = state) {
                    CoachServicesState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ProCircuit.Lime)
                    }
                    CoachServicesState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Błąd ładowania usług", color = ProCircuit.OnSurface)
                    }
                    is CoachServicesState.Content -> {
                        LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                            items(s.services) { service ->
                                ServiceCard(
                                    service = service,
                                    onDeactivate = { viewModel.onEvent(CoachServicesEvent.DeactivateService(service.id)) }
                                )
                            }
                            if (s.services.isEmpty()) {
                                item {
                                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🎾", fontSize = 40.sp)
                                            Spacer(Modifier.height(12.dp))
                                            Text("Dodaj swoją pierwszą usługę",
                                                fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp, color = ProCircuit.OnSurface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddSheet) {
            AddServiceSheet(
                onDismiss = { showAddSheet = false },
                onConfirm = { name, desc, type, price ->
                    viewModel.onEvent(CoachServicesEvent.AddService(name, desc, type, price))
                    showAddSheet = false
                }
            )
        }
    }
}

@Composable
private fun ServiceCard(service: CoachService, onDeactivate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp)).background(ProCircuit.SurfaceLow).padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(service.name, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, color = ProCircuit.OnBg)
                service.description?.let {
                    Text(it, fontFamily = AppBodyFontFamily, fontSize = 12.sp,
                        color = ProCircuit.OnSurface, maxLines = 2)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                val priceLabel = when (service.pricingType) {
                    PricingType.PER_HOUR -> "${service.priceCents / 100} zł/h"
                    PricingType.FIXED -> "${service.priceCents / 100} zł"
                    PricingType.PER_PERSON -> "${service.priceCents / 100} zł/os"
                }
                Text(priceLabel, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                    fontSize = 15.sp, color = ProCircuit.Lime)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onDeactivate, contentPadding = PaddingValues(0.dp)) {
            Text("Dezaktywuj", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold,
                fontSize = 11.sp, color = ProCircuit.OnSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddServiceSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String?, String, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var pricingType by remember { mutableStateOf("PER_HOUR") }
    var priceInput by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ProCircuit.SurfaceLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Nowa usługa", fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
                fontSize = 20.sp, color = ProCircuit.OnBg)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = name, onValueChange = { name = it },
                label = { Text("Nazwa") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = description, onValueChange = { description = it },
                label = { Text("Opis (opcjonalnie)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            // Pricing type selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("PER_HOUR" to "za godzinę", "FIXED" to "stała cena", "PER_PERSON" to "za osobę").forEach { (type, label) ->
                    FilterChip(
                        selected = pricingType == type,
                        onClick = { pricingType = type },
                        label = { Text(label, fontFamily = AppFontFamily, fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = priceInput, onValueChange = { priceInput = it },
                label = { Text("Cena (zł)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val priceCents = (priceInput.toDoubleOrNull() ?: 0.0).times(100).toInt()
                    if (name.isNotBlank() && priceCents > 0) {
                        onConfirm(name, description.ifBlank { null }, pricingType, priceCents)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProCircuit.Lime, contentColor = ProCircuit.Bg)
            ) {
                Text("DODAJ USŁUGĘ", fontFamily = AppFontFamily, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachServicesScreen.kt
git commit -m "feat: CoachServicesScreen — service list with add sheet"
```

---

## Task 17: UI — CoachProfileEditScreen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileEditScreen.kt`

**Step 1: Implement CoachProfileEditScreen**

Reuse the avatar upload flow from `SettingsScreen.kt` — copy the image picker logic verbatim.

```kotlin
object CoachProfileEditScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: CoachProfileEditViewModel = kmpViewModel()
        val state by viewModel.stateFlow.collectAsState()

        // Fields: avatar (tap → pick image → uploadAvatar()), bio, certifications chips, sports toggle
        // Save button calls updateProfile
        // Look at SettingsScreen.kt for exact pattern to follow
    }
}
```

Reference `SettingsScreen.kt` for:
- Avatar tap + image picker integration
- Bio text field
- Sport toggle chips
- Save button + success/error snackbar

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachProfileEditScreen.kt
git commit -m "feat: CoachProfileEditScreen — bio, certifications, sports, avatar"
```

---

## Task 18: UI — Update CoachDetailScreen (Player Booking Flow)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachDetailScreen.kt`

**Step 1: Replace availability section with service selection flow**

The new flow:
1. Show services list (from `state.services`) instead of hourly slots directly
2. Each service has a "Zarezerwuj" button
3. Tapping "Zarezerwuj" opens a bottom sheet:
   - If `PER_HOUR`: duration picker (60 / 90 / 120 min buttons)
   - Date+time slot picker (available slots from calendar)
   - Price summary
   - "Wyślij prośbę" button
4. After submit: snackbar "Prośba wysłana — czekaj na potwierdzenie trenera"

**Step 2: Update hero header** — replace `coach.hourlyRate / 100 zł` with:
```kotlin
val priceText = state.coach.lowestServicePriceCents?.let { "od ${it / 100} zł" } ?: "Brak usług"
Text(priceText, ...)
```

**Step 3: Implement BookingBottomSheet composable**

```kotlin
@Composable
private fun BookingBottomSheet(
    service: CoachService,
    availableSlots: List<BookingSlot>,
    onBook: (startsAt: Instant, endsAt: Instant, durationMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDuration by remember { mutableStateOf(60) }
    var selectedSlot by remember { mutableStateOf<BookingSlot?>(null) }

    // Duration picker (PER_HOUR only)
    // Slot list
    // Price summary: priceCents * durationMinutes / 60 for PER_HOUR, else priceCents
    // WYŚLIJ PROŚBĘ button
}
```

**Step 4: Run tests and build**

```bash
./gradlew :shared:jvmTest
```

**Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachDetailScreen.kt
git commit -m "feat: update CoachDetailScreen — service selection + booking request flow"
```

---

## Task 19: UI — Update CoachesScreen (Min Price Display)

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachesScreen.kt`

**Step 1: Update CoachCard price display**

Replace:
```kotlin
Text("${coach.hourlyRate / 100} zł", ...)
Text("/h", ...)
```

With:
```kotlin
val priceText = coach.lowestServicePriceCents?.let { "od ${it / 100} zł" } ?: "—"
Text(priceText, fontFamily = AppFontFamily, fontWeight = FontWeight.Black,
    fontSize = 18.sp, color = ProCircuit.Lime)
```

**Step 2: Commit**

```bash
git add shared/src/commonMain/kotlin/com/racketmatch/ui/coaches/CoachesScreen.kt
git commit -m "feat: CoachesScreen shows min service price instead of hourly_rate"
```

---

## Final Verification

**Step 1: Full backend test run**

```bash
cd backend && ./gradlew test
```

**Step 2: Full KMP test run**

```bash
./gradlew :shared:jvmTest
```

**Step 3: Manual smoke test flow**

1. Register new user with `isCoach=true` → verify `coach_profiles` row created
2. Login as coach → app opens in coach mode (CoachTabNavigator)
3. Switch to player mode → PlayerTabNavigator
4. In coach mode: add a service → appears in CoachServicesScreen
5. In coach mode: add calendar event (EXTERNAL_CLIENT) → appears in CoachCalendarScreen
6. In player mode: open CoachesScreen → see coach with "od X zł"
7. Open coach detail → services list → tap "Zarezerwuj" → select slot → submit
8. Switch back to coach mode → CoachBookingsScreen shows PENDING booking
9. Confirm booking → calendar event of type BOOKING auto-created
