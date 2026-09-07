package com.example.core

import android.content.Context

/**
 * MAX's Boss — the person MAX actually serves, injected into every system prompt (both the
 * classic REST flow in [com.example.data.api.MultiBrainManager] and Live Mode in
 * [MaxLiveService]) so MAX consistently knows who it's talking to, rather than treating every
 * session as a stranger.
 *
 * Base identity fields are editable in Settings but ship with the values the Boss gave when
 * this was set up. [personalizationNotes] is a free-text box for anything else worth MAX
 * knowing — preferences, routines, context — kept separate from the fixed identity fields so
 * it can grow without needing new dedicated fields for everything.
 */
object BossProfile {
    private const val PREFS_NAME = "max_jarvis_prefs"

    private const val KEY_NAME = "boss_name"
    private const val KEY_AGE = "boss_age"
    private const val KEY_GENDER = "boss_gender"
    private const val KEY_RELIGION = "boss_religion"
    private const val KEY_NOTES = "boss_personalization_notes"

    fun getName(context: Context): String = prefs(context).getString(KEY_NAME, "Fardeen") ?: "Fardeen"
    fun getAge(context: Context): String = prefs(context).getString(KEY_AGE, "19") ?: "19"
    fun getGender(context: Context): String = prefs(context).getString(KEY_GENDER, "Male") ?: "Male"
    fun getReligion(context: Context): String = prefs(context).getString(KEY_RELIGION, "Muslim") ?: "Muslim"
    fun getNotes(context: Context): String = prefs(context).getString(KEY_NOTES, "") ?: ""

    fun setName(context: Context, value: String) = putString(context, KEY_NAME, value)
    fun setAge(context: Context, value: String) = putString(context, KEY_AGE, value)
    fun setGender(context: Context, value: String) = putString(context, KEY_GENDER, value)
    fun setReligion(context: Context, value: String) = putString(context, KEY_RELIGION, value)
    fun setNotes(context: Context, value: String) = putString(context, KEY_NOTES, value)

    /** Text block prepended to every system prompt — both REST and Live Mode use this exact
     * function so the two modes never know the Boss differently. */
    fun buildIdentityPrompt(context: Context): String {
        val notes = getNotes(context)
        return buildString {
            append(
                "Your one and only real Boss is ${getName(context)}, age ${getAge(context)}, " +
                "${getGender(context)}, ${getReligion(context)}. Always treat ${getName(context)} " +
                "as your actual owner/master — not a generic user. Address him as 'Boss' or " +
                "'Sir' naturally. "
            )
            if (notes.isNotBlank()) {
                append("Additional things Boss has told you about himself/his preferences: $notes. ")
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun putString(context: Context, key: String, value: String) {
        prefs(context).edit().putString(key, value).apply()
    }
}
