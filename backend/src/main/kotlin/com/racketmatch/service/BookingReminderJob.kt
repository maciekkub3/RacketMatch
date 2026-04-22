package com.racketmatch.service

import com.racketmatch.domain.repository.BookingRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class BookingReminderJob(
    private val bookingRepository: BookingRepository,
    private val notificationService: NotificationService
) {

    /**
     * Fires hourly. Sends a BOOKING_REMINDER to both player and coach for
     * CONFIRMED bookings starting in [now+23h, now+25h], then flips
     * reminder_sent so the same booking isn't notified again.
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000, initialDelay = 30 * 1000)
    @Transactional
    fun sendReminders() {
        val now = Instant.now()
        val windowStart = now.plus(Duration.ofHours(23))
        val windowEnd = now.plus(Duration.ofHours(25))
        val due = bookingRepository.findPendingReminders(windowStart, windowEnd)
        if (due.isEmpty()) return

        due.forEach { booking ->
            val bookingId = booking.id?.toString() ?: return@forEach
            val title = "Przypomnienie o rezerwacji"
            val body = "Trening jutro — ${booking.service?.name ?: "sesja"}"
            val data = mapOf("bookingId" to bookingId)
            notificationService.send(booking.player.id!!, "BOOKING_REMINDER", title, body, data)
            notificationService.send(booking.coach.id!!, "BOOKING_REMINDER", title, body, data)
            booking.reminderSent = true
            booking.updatedAt = now
        }
        bookingRepository.saveAll(due)
    }
}
