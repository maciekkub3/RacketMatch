package com.racketmatch.presentation.viewmodel

fun Exception.toUserMessage(): String {
    val msg = message ?: return "Coś poszło nie tak. Spróbuj ponownie."
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
        msg.contains("Connection refused") -> "Brak połączenia z serwerem."
        else -> "Coś poszło nie tak. Spróbuj ponownie."
    }
}
