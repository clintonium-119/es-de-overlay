package com.clintonium.es_de_overlay.core

/**
 * Tells, from each app that comes to the front, when the game [frontend] launched has been left for [frontend]. The
 * game counts only once it has been in front, so the moment between the launch and the game coming up doesn't.
 */
class LeaveWatch(private val frontend: String) {
    private var seen: String? = null

    /**
     * Given the app in front and the launched game's app, returns the game's app once when it's been left. A launch
     * that's gone (null) keeps the game: a frontend Android ended for memory starts a new log when Home brings it back.
     */
    fun left(fg: String?, game: String?): String? {
        if (game != null && seen != game) seen = null
        if (game != null && fg == game) seen = game
        if (fg != frontend) return null
        return seen.also { seen = null }
    }

    /** Forgets the game, which was ended another way. */
    fun forget() {
        seen = null
    }
}
