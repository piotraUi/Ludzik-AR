package com.ludzik.game.characters

import com.ludzik.game.characters.doodle.DoodlePen

/** Animacje dostępne dla każdej postaci (wiersze w atlasie sprite'ów). */
enum class Anim(val fps: Float) {
    IDLE(5f),
    WALK(8f),
    RUN(12f),
    JUMP(8f),
    ACTION(9f),
    CLIMB(9f),
    HANG(4f),
}

/** Stany prostej maszyny stanów wspólnej dla wszystkich postaci. */
enum class State { IDLE, WALK, RUN, JUMP, REACT, PATH, ERASED }

/**
 * Pojedyncza postać w świecie. Renderer czyta tylko te właściwości;
 * całe zachowanie siedzi w [update] i [onTouched].
 */
interface Character {
    val id: Int
    val kind: CharacterKind
    val position: Vec3
    val surface: WalkSurface?
    val state: State

    /** Kierunek ruchu w poziomie (do odwracania sprite'a). */
    val heading: Vec3
    val anim: Anim
    val animFrame: Int

    /** 0..1 — przezroczystość (wymazywanie, pojawianie się). */
    val alpha: Float
    val isErased: Boolean

    /** Mnożnik rozmiaru (lekkie „squash & stretch”). */
    val scaleX: Float
    val scaleY: Float

    fun update(dt: Float, world: World)
    fun onTouched(world: World)

    /** Wymazanie przez Gumkę. Zwraca false, jeśli postać jest odporna. */
    fun erase(world: World, seconds: Float): Boolean
}

/**
 * Opis rodzaju postaci: nazwa, rozmiar, teksty dymków, rysowanie klatek i fabryka.
 * Nową postać dodaje się jedną klasą z `companion object : CharacterKind(...)`
 * oraz jedną linijką w [CharacterRegistry].
 */
abstract class CharacterKind(
    /** Stabilny identyfikator (np. do zapisu ustawień). */
    val id: String,
    val displayName: String,
    /** Bok kwadratowej klatki sprite'a w metrach. */
    val spriteSizeMeters: Float,
    val phrases: List<String>,
) {
    open val framesPerAnim: Int = 4

    /** Rysuje jedną klatkę w jednostkowym kwadracie (0..1, Y w dół, ziemia na y≈0.95). */
    abstract fun draw(pen: DoodlePen, anim: Anim, frame: Int)

    abstract fun create(id: Int, position: Vec3, surface: WalkSurface): Character
}
