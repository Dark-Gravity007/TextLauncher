package com.textlauncher.app.data

/** Streak maths for the habit tracker. Days are whole-number day counts (epoch day in the local time zone). */
object HabitLogic {

    fun today(nowMs: Long = System.currentTimeMillis(), offsetMs: Long = java.util.TimeZone.getDefault().getOffset(nowMs).toLong()): Int =
        Math.floorDiv(nowMs + offsetMs, 86_400_000L).toInt()

    class State(val lastDone: Int, val streak: Int)

    /** Tapping a habit marks today done, or undoes it if it was already done today. */
    fun toggle(state: State, today: Int): State {
        return if (state.lastDone == today) {
            val streak = (state.streak - 1).coerceAtLeast(0)
            State(if (streak > 0) today - 1 else 0, streak)
        } else {
            val streak = if (state.lastDone == today - 1) state.streak + 1 else 1
            State(today, streak)
        }
    }

    fun doneToday(state: State, today: Int) = state.lastDone == today

    /** A streak only counts while it is alive: done today or yesterday. */
    fun visibleStreak(state: State, today: Int): Int =
        if (state.lastDone == today || state.lastDone == today - 1) state.streak else 0
}
