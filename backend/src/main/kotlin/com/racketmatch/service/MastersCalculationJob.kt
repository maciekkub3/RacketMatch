package com.racketmatch.service

import com.racketmatch.domain.repository.UserRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MastersCalculationJob(private val userRepository: UserRepository) {

    @Scheduled(cron = "0 0 3 * * MON")  // Poniedziałek 3:00
    @Transactional
    fun recalculateMasters() {
        val cities = userRepository.findDistinctCities()
        cities.forEach { city ->
            val topPlayers = userRepository
                .findByCityAndMatchesPlayedGreaterThanEqual(city, 20)
                .sortedByDescending { it.eloRating }
            val topCount = maxOf(1, (topPlayers.size * 0.05).toInt())
            val masterIds = topPlayers.take(topCount).map { it.id }.toSet()

            topPlayers.forEach { player ->
                userRepository.save(player.copy(isMaster = player.id in masterIds))
            }
        }
    }
}
