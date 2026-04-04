package com.racketmatch.presentation.viewmodel

fun Exception.toUserMessage(): String {
    val msg = message ?: return "Coś poszło nie tak. Spróbuj ponownie."
    println("RacketMatch error: ${this::class.simpleName}: $msg")
    return when {
        msg.contains("401") -> "Nieprawidłowy email lub hasło."
        msg.contains("409") -> "Konto z tym emailem już istnieje."
        msg.contains("400") -> "Sprawdź poprawność danych i spróbuj ponownie."
        msg.contains("403") -> "Brak uprawnień."
        msg.contains("404") -> "Nie znaleziono zasobu."
        msg.contains("500") || msg.contains("502") || msg.contains("503") -> "Błąd serwera. Spróbuj później."
        msg.contains("Unable to resolve host") ||
        msg.contains("UnresolvedAddress") ||
        msg.contains("ConnectException") ||
        msg.contains("SocketException") ||
        msg.contains("SocketTimeout") ||
        msg.contains("Timeout") ||
        msg.contains("Connection refused") ||
        msg.contains("timed out") -> "Brak połączenia z serwerem."
        else -> "Coś poszło nie tak. Spróbuj ponownie. [${this::class.simpleName}]"
    }
}

/** Use during registration — 401 doesn't mean "wrong password" here. */
fun Exception.toRegistrationMessage(): String {
    val msg = message ?: return "Coś poszło nie tak. Spróbuj ponownie."
    return when {
        msg.contains("409") -> "Konto z tym emailem już istnieje."
        msg.contains("400") || msg.contains("401") -> "Sprawdź poprawność danych i spróbuj ponownie."
        msg.contains("500") || msg.contains("502") || msg.contains("503") -> "Błąd serwera. Spróbuj później."
        msg.contains("Unable to resolve host") ||
        msg.contains("UnresolvedAddress") ||
        msg.contains("ConnectException") ||
        msg.contains("SocketException") ||
        msg.contains("Connection refused") -> "Brak połączenia z serwerem."
        else -> "Coś poszło nie tak. Spróbuj ponownie."
    }
}
