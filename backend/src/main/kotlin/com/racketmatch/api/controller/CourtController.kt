package com.racketmatch.api.controller

import com.racketmatch.api.dto.CourtDto
import com.racketmatch.api.dto.toDto
import com.racketmatch.domain.repository.CourtRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/courts")
class CourtController(private val courtRepository: CourtRepository) {

    @GetMapping
    fun getCourts(@RequestParam(defaultValue = "Warszawa") city: String): List<CourtDto> =
        courtRepository.findByCity(city).map { it.toDto() }
}
