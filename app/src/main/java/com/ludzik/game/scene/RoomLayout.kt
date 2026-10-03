// PLIK WYGENEROWANY przez tools/build_assets.py — nie edytuj ręcznie.
package com.ludzik.game.scene

import com.ludzik.game.characters.Vec3
import com.ludzik.game.math.Aabb

/** Układ pokoju: wymiary, meble (model, pozycja, obrót) i przedmioty do respienia. */
object RoomLayout {
    const val HALF_X = 2.6000f
    const val HALF_Z = 2.2000f
    const val HEIGHT = 2.7000f
    val window = Aabb(Vec3(-0.8500f, 0.8500f, -2.4000f), Vec3(0.8500f, 2.2500f, -2.2000f))

    val furniture: List<FurnitureSpec> = listOf(
        FurnitureSpec("models/sofa_02.glb", Vec3(-1.9812f, -0.0002f, 0.1503f), 90.0000f, Aabb(Vec3(-2.4589f, 0.0000f, -0.7536f), Vec3(-1.6411f, 0.7095f, 1.0536f)), collides = true, floorObstacle = true, walk = WalkTop(0.3335f, -2.1481f, -0.7536f, -1.6411f, 1.0536f)),
        FurnitureSpec("models/throw_pillows_01.glb", Vec3(-2.2719f, 0.2756f, 0.1562f), 90.0000f, Aabb(Vec3(-2.5137f, 0.2735f, -0.3608f), Vec3(-1.8863f, 0.7236f, 0.6608f)), collides = false, floorObstacle = true, walk = null),
        FurnitureSpec("models/modern_coffee_table_01.glb", Vec3(-0.9502f, 0.0001f, 0.1500f), 90.0000f, Aabb(Vec3(-1.5509f, 0.0000f, -0.1500f), Vec3(-0.3491f, 0.3900f, 0.4500f)), collides = true, floorObstacle = true, walk = WalkTop(0.3900f, -1.5509f, -0.1500f, -0.3491f, 0.4500f)),
        FurnitureSpec("models/modern_arm_chair_01.glb", Vec3(-0.8966f, -0.0004f, 1.4178f), 180.0000f, Aabb(Vec3(-1.3102f, 0.0000f, 1.0067f), Vec3(-0.4898f, 1.0229f, 1.9933f)), collides = true, floorObstacle = true, walk = WalkTop(0.4603f, -1.3102f, 1.0067f, -0.4898f, 1.5987f)),
        FurnitureSpec("models/side_table_01.glb", Vec3(-2.2500f, 0.0024f, -1.4500f), 90.0000f, Aabb(Vec3(-2.4750f, 0.0000f, -1.7250f), Vec3(-2.0250f, 0.5507f, -1.1750f)), collides = true, floorObstacle = true, walk = WalkTop(0.5507f, -2.4750f, -1.7250f, -2.0250f, -1.1750f)),
        FurnitureSpec("models/round_wooden_table_02.glb", Vec3(1.4500f, 0.0000f, 0.7500f), 0.0000f, Aabb(Vec3(1.0519f, 0.0000f, 0.3519f), Vec3(1.8481f, 0.7460f, 1.1481f)), collides = true, floorObstacle = false, walk = WalkTop(0.7460f, 1.0519f, 0.3519f, 1.8481f, 1.1481f)),
        FurnitureSpec("models/dining_chair_02.glb", Vec3(1.4500f, -0.0013f, 1.3072f), 180.0000f, Aabb(Vec3(1.2332f, 0.0000f, 1.0618f), Vec3(1.6668f, 0.9734f, 1.6382f)), collides = true, floorObstacle = true, walk = WalkTop(0.4867f, 1.2332f, 1.0618f, 1.6668f, 1.4941f)),
        FurnitureSpec("models/dining_chair_02.glb", Vec3(0.8928f, -0.0013f, 0.7500f), 90.0000f, Aabb(Vec3(0.5618f, 0.0000f, 0.5332f), Vec3(1.1382f, 0.9734f, 0.9668f)), collides = true, floorObstacle = true, walk = WalkTop(0.4867f, 0.7059f, 0.5332f, 1.1382f, 0.9668f)),
        FurnitureSpec("models/wooden_bookshelf_worn.glb", Vec3(2.3500f, 0.0000f, -1.0500f), -90.0000f, Aabb(Vec3(2.0593f, 0.0000f, -1.7370f), Vec3(2.6407f, 2.0634f, -0.3630f)), collides = true, floorObstacle = true, walk = WalkTop(2.0634f, 2.0593f, -1.7370f, 2.6407f, -0.3630f)),
        FurnitureSpec("models/potted_plant_02.glb", Vec3(1.7961f, -0.0001f, -1.7584f), 0.0000f, Aabb(Vec3(1.3994f, 0.0000f, -2.1787f), Vec3(2.1006f, 0.8412f, -1.5213f)), collides = true, floorObstacle = true, walk = null),
        FurnitureSpec("models/modern_ceiling_lamp_01.glb", Vec3(0.0000f, 1.5274f, -0.0015f), 0.0000f, Aabb(Vec3(-0.2158f, 1.7484f, -0.2158f), Vec3(0.2158f, 2.7000f, 0.2158f)), collides = false, floorObstacle = true, walk = null),
        FurnitureSpec("models/fancy_picture_frame_01.glb", Vec3(-2.5919f, 1.3500f, 0.1500f), 90.0000f, Aabb(Vec3(-2.5900f, 1.1179f, -0.1515f), Vec3(-2.5700f, 1.5821f, 0.4515f)), collides = false, floorObstacle = true, walk = null),
    )

    val spawnables: List<SpawnableSpec> = listOf(
        SpawnableSpec("dirty_football", "Piłka", "models/dirty_football.glb", radius = 0.1107f, halfHeight = 0.1100f, modelCenter = Vec3(0.0039f, 0.1094f, -0.0001f), bounce = 0.7200f, friction = 0.3500f, rolls = true, mass = 0.4500f),
        SpawnableSpec("rubber_duck_toy", "Kaczka", "models/rubber_duck_toy.glb", radius = 0.1332f, halfHeight = 0.1428f, modelCenter = Vec3(0.0000f, 0.1427f, 0.0039f), bounce = 0.4500f, friction = 0.6000f, rolls = false, mass = 0.1000f),
        SpawnableSpec("cardboard_box_01", "Karton", "models/cardboard_box_01.glb", radius = 0.2074f, halfHeight = 0.1709f, modelCenter = Vec3(0.0010f, 0.1641f, 0.0329f), bounce = 0.1500f, friction = 0.8000f, rolls = false, mass = 0.6000f),
        SpawnableSpec("food_apple_01", "Jabłko", "models/food_apple_01.glb", radius = 0.0488f, halfHeight = 0.0427f, modelCenter = Vec3(0.0004f, 0.0427f, 0.0021f), bounce = 0.3500f, friction = 0.5000f, rolls = true, mass = 0.2000f),
        SpawnableSpec("wooden_crate_01", "Skrzynka", "models/wooden_crate_01.glb", radius = 0.2640f, halfHeight = 0.1748f, modelCenter = Vec3(0.0000f, 0.1669f, 0.0083f), bounce = 0.1000f, friction = 0.9000f, rolls = false, mass = 3.0000f),
    )
}
