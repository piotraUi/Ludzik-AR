package com.ludzik.ar.ar

import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import com.ludzik.ar.characters.Vec3
import com.ludzik.ar.characters.WalkSurface

/**
 * Tłumaczy płaszczyzny ARCore na [WalkSurface] używane przez logikę postaci.
 * Obiekty WalkSurface są stabilne między klatkami (postacie trzymają do nich referencje).
 */
class PlaneSurfaces {
    private val byPlane = HashMap<Plane, WalkSurface>()
    private var nextId = 1
    private val local = FloatArray(3)
    private val world = FloatArray(3)

    val surfaces = ArrayList<WalkSurface>()

    fun update(planes: Collection<Plane>): List<WalkSurface> {
        surfaces.clear()
        for (plane in planes) {
            if (plane.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING) continue
            val s = byPlane.getOrPut(plane) { WalkSurface(nextId++, plane.type == Plane.Type.VERTICAL) }
            // Płaszczyzna połączona z inną albo porzucona — postacie przejdą na inną.
            if (plane.trackingState == TrackingState.STOPPED || plane.subsumedBy != null) {
                s.alive = false
                continue
            }
            if (plane.trackingState == TrackingState.TRACKING) refresh(plane, s)
            if (s.boundary.size >= 3) surfaces.add(s)
        }
        byPlane.entries.removeAll { (plane, s) ->
            val dead = plane.trackingState == TrackingState.STOPPED || plane.subsumedBy != null
            if (dead) s.alive = false
            dead
        }
        return surfaces
    }

    fun surfaceFor(plane: Plane): WalkSurface? {
        var p = plane
        while (true) p = p.subsumedBy ?: break
        return byPlane[p]
    }

    private fun refresh(plane: Plane, s: WalkSurface) {
        val polygon = plane.polygon
        val pose = plane.centerPose
        val n = polygon.limit() / 2
        val boundary = ArrayList<Vec3>(n)
        for (i in 0 until n) {
            local[0] = polygon.get(i * 2)
            local[1] = 0f
            local[2] = polygon.get(i * 2 + 1)
            pose.transformPoint(local, 0, world, 0)
            boundary.add(Vec3(world[0], world[1], world[2]))
        }
        val normal = pose.getTransformedAxis(1, 1f)
        s.update(boundary, Vec3(pose.tx(), pose.ty(), pose.tz()), Vec3(normal[0], normal[1], normal[2]))
    }

    fun clear() {
        byPlane.clear()
        surfaces.clear()
    }
}
