package com.ludzik.game.scene

import com.ludzik.game.characters.Vec3
import com.ludzik.game.math.Aabb

/** Prostokąt (x0,z0)-(x1,z1) na wysokości y, po którym mogą chodzić ludziki (blat, siedzisko). */
class WalkTop(val y: Float, val x0: Float, val z0: Float, val x1: Float, val z1: Float)

/** Mebel z pliku glb ustawiony w pokoju (obrót wokół osi Y w stopniach). */
class FurnitureSpec(
    val file: String,
    val position: Vec3,
    val rotationDeg: Float,
    val bounds: Aabb,
    val collides: Boolean,
    /** Czy ludziki muszą go obchodzić na podłodze (np. sofa), czy mogą wejść pod spód (stół). */
    val floorObstacle: Boolean,
    val walk: WalkTop?,
)

/** Przedmiot, który gracz może respić albo rzucić. */
class SpawnableSpec(
    val id: String,
    val name: String,
    val file: String,
    val radius: Float,
    val halfHeight: Float,
    /** Środek bryły modelu w jego własnym układzie (do ustawienia modelu na środku ciała fizycznego). */
    val modelCenter: Vec3,
    val bounce: Float,
    val friction: Float,
    /** Toczy się jak piłka (kula) czy stoi płasko jak pudełko. */
    val rolls: Boolean,
    val mass: Float,
)
