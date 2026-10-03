package com.ludzik.game.characters

/** Rejestr wszystkich postaci. Kolejność = kolejność w menu wyboru. */
object CharacterRegistry {
    val kinds: List<CharacterKind> = listOf(
        Ludzik,
        Kleks,
        Gumka,
        Olowek,
        Pajak,
    )

    fun byId(id: String): CharacterKind? = kinds.firstOrNull { it.id == id }
}
