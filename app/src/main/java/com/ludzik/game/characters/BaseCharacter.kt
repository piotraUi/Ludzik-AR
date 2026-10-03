package com.ludzik.game.characters

import com.ludzik.game.audio.Haptic
import com.ludzik.game.audio.Sfx
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Wspólna maszyna stanów: IDLE → WALK/RUN → JUMP/PATH → REACT, plus ERASED.
 * Podklasy decydują *co* robić ([decide], [sense]), a baza wie *jak* to zrobić
 * (chodzenie z omijaniem krawędzi, skoki na inne płaszczyzny, ścieżki po mostach).
 */
abstract class BaseCharacter(
    final override val id: Int,
    final override val kind: CharacterKind,
    start: Vec3,
    startSurface: WalkSurface,
) : Character {

    final override var position: Vec3 = start
        protected set
    final override var surface: WalkSurface? = startSurface
        protected set
    final override var state: State = State.IDLE
        private set
    final override var heading: Vec3 = Vec3(1f, 0f, 0f)
        protected set
    final override var alpha: Float = 0f
        protected set
    final override var scaleX = 1f
        protected set
    final override var scaleY = 1f
        protected set
    final override val isErased get() = state == State.ERASED

    protected var currentAnim = Anim.IDLE
    final override val anim get() = currentAnim
    private var animTime = 0f
    final override val animFrame get() = (animTime * currentAnim.fps).toInt() % kind.framesPerAnim

    /** Czas w bieżącym stanie. */
    protected var stateTime = 0f
        private set
    private var stateDuration = 0f

    // Chodzenie
    protected var target: Vec3? = null
        private set
    private var onArrive: (() -> Unit)? = null

    // Skok
    private var jumpFrom = Vec3.ZERO
    private var jumpTo = Vec3.ZERO
    private var jumpApex = 0f
    private var jumpDuration = 0.5f
    private var jumpSurface: WalkSurface? = null
    private var jumpOnLand: (() -> Unit)? = null

    // Ścieżka (most, wspinaczka, nitka)
    private var path: List<Vec3> = emptyList()
    private var pathIndex = 0
    private var pathSpeed = 0.1f
    private var pathAnim = Anim.WALK
    private var pathSurface: WalkSurface? = null
    private var pathOnStep: ((Vec3) -> Unit)? = null
    private var pathOnDone: (() -> Unit)? = null

    private var eraseTimer = 0f

    /** Postać wisi/wspina się poza płaszczyzną (np. na ścianie) — bez przyciągania do podłogi. */
    protected var airborne = false

    open val walkSpeed = 0.07f
    open val runSpeed = 0.18f
    open val erasable = true
    protected open val edgeMargin = 0.06f

    protected lateinit var world: World
        private set

    protected val rnd get() = world.random

    // ---------------------------------------------------------------- pętla

    final override fun update(dt: Float, world: World) {
        this.world = world
        animTime += dt
        stateTime += dt

        if (state == State.ERASED) {
            updateErased(dt)
            return
        }
        if (alpha < 1f) alpha = min(1f, alpha + dt / 0.6f)
        relaxScale(dt)
        ensureSurface()
        sense(dt)

        when (state) {
            State.IDLE -> {
                currentAnim = Anim.IDLE
                snapToGround(dt)
                if (stateTime >= stateDuration) decide()
            }
            State.WALK, State.RUN -> updateMove(dt)
            State.JUMP -> updateJump()
            State.PATH -> updatePath(dt)
            State.REACT -> {
                snapToGround(dt)
                if (stateTime >= stateDuration) {
                    setState(State.IDLE, 0.2f)
                    onReactDone()
                }
            }
            State.ERASED -> Unit
        }
        afterUpdate(dt)
    }

    // ---------------------------------------------------------------- hooki podklas

    /** Wybór następnej czynności, gdy poprzednia się skończyła. */
    protected open fun decide() = defaultDecide()

    /** Wywoływane co klatkę przed logiką stanu (np. ucieczka przed Gumką). */
    protected open fun sense(dt: Float) {}

    protected open fun afterUpdate(dt: Float) {}
    protected open fun onLanded() {}
    protected open fun onMoved(distance: Float) {}
    protected open fun onReactDone() {}
    protected open fun onReturnFromErase() {
        world.sound(Sfx.SCRIBBLE, 0.6f)
    }

    // ---------------------------------------------------------------- akcje

    protected fun setState(s: State, duration: Float = 0f) {
        state = s
        stateTime = 0f
        stateDuration = duration
    }

    private fun clearActions() {
        target = null
        onArrive = null
        jumpOnLand = null
        pathOnDone = null
        pathOnStep = null
    }

    fun idle(seconds: Float) {
        clearActions()
        setState(State.IDLE, seconds)
        currentAnim = Anim.IDLE
    }

    fun walkTo(p: Vec3, run: Boolean = false, then: (() -> Unit)? = null) {
        clearActions()
        target = p
        onArrive = then
        setState(if (run) State.RUN else State.WALK)
        currentAnim = if (run) Anim.RUN else Anim.WALK
    }

    /** Pozwala gonić ruchomy cel bez kasowania callbacku. */
    protected fun retarget(p: Vec3) {
        target = p
    }

    fun react(anim: Anim, seconds: Float) {
        clearActions()
        setState(State.REACT, seconds)
        currentAnim = anim
    }

    fun jumpTo(dest: Vec3, destSurface: WalkSurface?, apex: Float = 0.12f, then: (() -> Unit)? = null) {
        clearActions()
        jumpFrom = position
        jumpTo = dest
        val dh = abs(dest.y - position.y)
        jumpApex = apex + dh * 0.5f
        jumpDuration = 0.42f + dh * 0.45f + min(0.3f, position.horizontalDistanceTo(dest) * 0.6f)
        jumpSurface = destSurface
        jumpOnLand = then
        val dir = (dest - position).horizontal()
        if (dir.length() > 0.01f) heading = dir.normalized()
        setState(State.JUMP)
        currentAnim = Anim.JUMP
        scaleX = 0.85f
        scaleY = 1.2f
        world.sound(Sfx.BOING, 0.7f)
    }

    /** Podskok w miejscu. */
    fun hop(height: Float = 0.1f) = jumpTo(position, surface, height)

    fun followPath(
        points: List<Vec3>,
        speed: Float,
        anim: Anim,
        destSurface: WalkSurface?,
        onStep: ((Vec3) -> Unit)? = null,
        then: (() -> Unit)? = null,
    ) {
        clearActions()
        if (points.isEmpty()) {
            idle(0.3f)
            return
        }
        path = points
        pathIndex = 0
        pathSpeed = speed
        pathAnim = anim
        pathSurface = destSurface
        pathOnStep = onStep
        pathOnDone = then
        setState(State.PATH)
        currentAnim = anim
    }

    // ---------------------------------------------------------------- wyższe akcje

    /** Spacer w losowe miejsce na bieżącej powierzchni. */
    protected fun wander(run: Boolean = false, radius: Float = 0.6f): Boolean {
        val s = surface ?: return false
        val p = s.randomPointNear(rnd, position, radius, edgeMargin) ?: return false
        walkTo(p, run)
        return true
    }

    /** Ucieczka od zagrożenia — próbuje kilku kierunków, zanim się podda. */
    protected fun flee(threat: Vec3, distance: Float = 0.45f): Boolean {
        val s = surface ?: return false
        var away = (position - threat).horizontal().normalized()
        if (away == Vec3.ZERO) away = randomDirection()
        for (deg in intArrayOf(0, 35, -35, 70, -70, 110, -110, 150, -150)) {
            val dir = rotateY(away, deg * 0.017453292f)
            val p = position + dir * distance
            if (s.contains(p, edgeMargin)) {
                walkTo(p.withY(s.height), run = true)
                return true
            }
        }
        return tryVisitOtherSurface()
    }

    class Transfer(val takeoff: Vec3, val landing: Vec3, val path: List<Vec3>?)

    /** Szuka sposobu przejścia na [dest]: najpierw most, potem skok przez krawędź. */
    protected fun planTransfer(dest: WalkSurface): Transfer? {
        val src = surface ?: return null
        if (dest === src || !dest.alive || dest.isVertical) return null
        world.bridgeBetween(src, dest)?.let { b ->
            val pts = if (b.from === src) b.points.toList() else b.points.reversed()
            if (pts.size >= 2) return Transfer(pts.first(), pts.last(), pts)
        }
        val dh = dest.height - src.height
        if (abs(dh) > World.MAX_JUMP_HEIGHT) return null
        val high = if (dh > 0f) dest else src
        val low = if (dh > 0f) src else dest
        var best: Transfer? = null
        var bestDist = Float.MAX_VALUE
        for (v in high.boundary) {
            val d = (v - high.center).horizontal().normalized()
            if (d == Vec3.ZERO) continue
            val lowP = Vec3(v.x + d.x * 0.16f, low.height, v.z + d.z * 0.16f)
            val highP = Vec3(v.x - d.x * 0.08f, high.height, v.z - d.z * 0.08f)
            if (!low.contains(lowP, 0.02f) || !high.contains(highP, 0.01f)) continue
            val takeoff = if (src === low) lowP else highP
            val landing = if (src === low) highP else lowP
            val dist = takeoff.horizontalDistanceTo(position)
            if (dist < bestDist) {
                bestDist = dist
                best = Transfer(takeoff, landing, null)
            }
        }
        return best
    }

    protected fun goToSurface(dest: WalkSurface, run: Boolean = false, then: (() -> Unit)? = null): Boolean {
        val tr = planTransfer(dest) ?: return false
        walkTo(tr.takeoff, run) {
            if (tr.path != null) {
                followPath(tr.path, walkSpeed * 1.3f, if (run) Anim.RUN else Anim.WALK, dest, then = then)
            } else {
                jumpTo(tr.landing, dest, 0.12f, then)
            }
        }
        return true
    }

    /** Losowa wyprawa na stół/kanapę albo z powrotem na podłogę. */
    protected fun tryVisitOtherSurface(): Boolean {
        val src = surface ?: return false
        val candidates = world.horizontalSurfaces.filter {
            it !== src && it.area > 0.04f && abs(it.height - src.height) < World.MAX_JUMP_HEIGHT
        }.shuffled(rnd)
        for (c in candidates) if (goToSurface(c)) return true
        return false
    }

    protected fun defaultDecide() {
        val s = surface
        if (s == null) {
            idle(1f)
            return
        }
        if (!s.contains(position, 0f)) {
            s.randomPoint(rnd, edgeMargin)?.let {
                walkTo(it)
                return
            }
        }
        val r = rnd.nextFloat()
        when {
            r < 0.30f -> idle(1f + rnd.nextFloat() * 2f)
            r < 0.72f -> if (!wander()) idle(1f)
            r < 0.80f -> if (!wander(run = true, radius = 0.9f)) idle(1f)
            r < 0.90f -> if (!tryVisitOtherSurface()) wander()
            else -> react(Anim.ACTION, 1.4f)
        }
    }

    // ---------------------------------------------------------------- dotyk i wymazywanie

    override fun onTouched(world: World) {
        this.world = world
        if (isErased) return
        world.say(this, kind.phrases.random(rnd))
        world.sound(Sfx.HEY)
        world.haptic(Haptic.LIGHT)
        if (state == State.PATH || state == State.JUMP || airborne) {
            scaleX = 1.3f
            scaleY = 0.8f
            return
        }
        hop(0.12f)
    }

    override fun erase(world: World, seconds: Float): Boolean {
        this.world = world
        if (!erasable || isErased || state == State.PATH || state == State.JUMP || airborne) return false
        clearActions()
        setState(State.ERASED)
        eraseTimer = seconds
        return true
    }

    private fun updateErased(dt: Float) {
        alpha = max(0f, alpha - dt / 0.4f)
        eraseTimer -= dt
        if (eraseTimer <= 0f) {
            alpha = 0f
            setState(State.IDLE, 0.8f)
            currentAnim = Anim.IDLE
            onReturnFromErase()
        }
    }

    // ---------------------------------------------------------------- fizyka

    private fun ensureSurface() {
        if (state == State.JUMP || state == State.PATH || airborne) return
        val s = surface
        if (s != null && s.alive) return
        val under = world.surfaceUnder(position, 0.3f) ?: world.nearestHorizontal(position) ?: return
        surface = under
        if (under.contains(position, -0.02f) && under.height < position.y - 0.05f) {
            // Płaszczyzna zniknęła spod nóg — spadamy na niższą.
            jumpTo(position.withY(under.height), under, 0.02f)
        }
    }

    private fun snapToGround(dt: Float) {
        if (airborne) return
        val s = surface ?: return
        val k = min(1f, dt * 10f)
        position = position.withY(position.y + (s.height - position.y) * k)
    }

    private fun updateMove(dt: Float) {
        val t = target
        if (t == null) {
            decide()
            return
        }
        val speed = if (state == State.RUN) runSpeed else walkSpeed
        currentAnim = if (state == State.RUN) Anim.RUN else Anim.WALK
        val to = (t - position).horizontal()
        val dist = to.length()
        val step = speed * dt
        if (dist <= max(0.005f, step)) {
            position = Vec3(t.x, position.y, t.z)
            onMoved(dist)
            snapToGround(dt)
            val cb = onArrive
            target = null
            onArrive = null
            setState(State.IDLE, 0.15f + rnd.nextFloat() * 0.3f)
            currentAnim = Anim.IDLE
            if (cb != null) cb() else if (rnd.nextFloat() < 0.5f) decide()
            return
        }
        val dir = to * (1f / dist)
        heading = dir
        val next = position + dir * step
        val s = surface
        if (s != null && s.contains(position, 0f) && !s.contains(next, 0f)) {
            // Krawędź płaszczyzny — nie spadamy, tylko wymyślamy coś innego.
            idle(0.3f)
            return
        }
        position = next
        onMoved(step)
        snapToGround(dt)
    }

    private fun updateJump() {
        val t = min(1f, stateTime / jumpDuration)
        val base = jumpFrom.lerp(jumpTo, t)
        position = base.withY(base.y + 4f * jumpApex * t * (1f - t))
        if (t >= 1f) {
            position = jumpTo
            jumpSurface?.let { surface = it }
            scaleX = 1.25f
            scaleY = 0.78f
            val cb = jumpOnLand
            jumpOnLand = null
            setState(State.IDLE, 0.25f + rnd.nextFloat() * 0.4f)
            currentAnim = Anim.IDLE
            onLanded()
            cb?.invoke()
        }
    }

    private fun updatePath(dt: Float) {
        currentAnim = pathAnim
        var remaining = pathSpeed * dt
        while (remaining > 0f && pathIndex < path.size) {
            val p = path[pathIndex]
            val to = p - position
            val d = to.length()
            if (d <= remaining) {
                position = p
                remaining -= d
                pathIndex++
            } else {
                position = position + to * (remaining / d)
                remaining = 0f
            }
            val h = to.horizontal()
            if (h.length() > 0.002f) heading = h.normalized()
        }
        pathOnStep?.invoke(position)
        if (pathIndex >= path.size) {
            pathSurface?.let { surface = it }
            val cb = pathOnDone
            pathOnDone = null
            pathOnStep = null
            setState(State.IDLE, 0.3f)
            currentAnim = Anim.IDLE
            cb?.invoke()
        }
    }

    private fun relaxScale(dt: Float) {
        val k = min(1f, dt * 8f)
        scaleX += (1f - scaleX) * k
        scaleY += (1f - scaleY) * k
    }

    // ---------------------------------------------------------------- pomocnicze

    protected fun randomDirection(): Vec3 {
        val a = rnd.nextFloat() * 6.2831855f
        return Vec3(cos(a), 0f, sin(a))
    }

    protected fun rotateY(v: Vec3, rad: Float): Vec3 {
        val c = cos(rad)
        val s = sin(rad)
        return Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c)
    }

    protected fun faceTowards(p: Vec3) {
        val d = (p - position).horizontal()
        if (d.length() > 0.001f) heading = d.normalized()
    }

    protected fun sameLevel(other: Character) =
        other.surface === surface && abs(other.position.y - position.y) < 0.1f
}
