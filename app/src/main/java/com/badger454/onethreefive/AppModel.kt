package com.badger454.onethreefive

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.badger454.onethreefive.data.Store
import com.badger454.onethreefive.domain.AppState
import com.badger454.onethreefive.domain.Planner
import com.badger454.onethreefive.domain.Section
import com.badger454.onethreefive.domain.SystemClock
import com.badger454.onethreefive.notify.Nudges
import com.badger454.onethreefive.notify.Reminders
import com.badger454.onethreefive.widget.DotsWidget
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

enum class Tab { DAY, BUCKET, PROJECTS }

/** Bottom sheets and dialogs. Only one is open at a time. */
sealed class Sheet {
    data class Dest(val taskId: String, val copy: Boolean, val project: Boolean = false) : Sheet()
    data class TodayPick(val taskId: String) : Sheet()
    data class PickProject(val taskId: String) : Sheet()
    data class ShareAdd(val text: String) : Sheet()
    data class Remind(val taskId: String) : Sheet()
    data class Repeat(val taskId: String) : Sheet()
    data class Notes(val taskId: String) : Sheet()
    data class EditScope(val taskId: String, val previousTitle: String) : Sheet()
    data class DeleteScope(val taskId: String) : Sheet()
    data class CarriedDone(val taskId: String, val from: String) : Sheet()
    data class Categories(val back: Sheet? = null) : Sheet()
    data class ProjectMenu(val projectId: String) : Sheet()
    object Settings : Sheet()
}

data class UiMessage(val text: String, val undo: Boolean = false, val id: Long = System.nanoTime())

/**
 * App-wide state holder: the planner (all data and rules), what's on screen, and persistence.
 * Lives in the Application, so it survives rotation and is shared with the notification receivers.
 */
class AppModel(private val context: Context) {
    private val store = Store(context)
    val planner: Planner

    /** Bumped after every change; screens read it so they redraw. */
    var version by mutableIntStateOf(0)
        private set

    var tab by mutableStateOf(Tab.DAY)
    var viewDate by mutableStateOf("")
    var sheet by mutableStateOf<Sheet?>(null)

    /** Which slot or task text field should take focus next ("<list>:<index>"). */
    var focusKey by mutableStateOf<String?>(null)

    val messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var undoSnapshot: String? = null

    init {
        val json = store.load()
        var state: AppState? = null
        if (json != null) {
            try {
                state = AppState.fromJson(json)
            } catch (e: Exception) {
                // Keep the unreadable file instead of silently overwriting it.
                Log.e("AppModel", "Saved data unreadable", e)
                try { File(context.filesDir, "state.unreadable.json").writeText(json) } catch (_: Exception) {}
            }
        }
        planner = Planner(state ?: AppState(), SystemClock)
        if (state == null) {
            planner.seedExamples()
            store.save(planner.state.toJson())
        }
        planner.rollover()
        viewDate = planner.today()
    }

    // ---------- changes ----------
    /** Apply a change, then save and redraw. With [undoable], the previous state can be restored once. */
    fun change(undoable: Boolean = false, block: Planner.() -> Unit) {
        if (undoable) undoSnapshot = planner.snapshot()
        planner.block()
        commit()
    }

    fun commit() {
        version++
        store.save(planner.state.toJson())
        afterChange()
    }

    /** Keeps everything outside the app in step with the data: reminders, nudges, the home-screen widget. */
    private fun afterChange() {
        Reminders.sync(context, planner)
        Nudges.schedule(context, planner)
        try { DotsWidget.refresh(context) } catch (_: Exception) {}
    }

    /** Text edits: save without redrawing every screen on each keystroke. */
    fun saveQuietly() {
        store.save(planner.state.toJson())
    }

    /** For receivers: save before the process may be stopped. */
    fun commitNow() {
        version++
        store.saveNow(planner.state.toJson())
        afterChange()
    }

    fun undo() {
        val s = undoSnapshot ?: return
        undoSnapshot = null
        planner.restore(s)
        commit()
    }

    fun say(text: String, undo: Boolean = false) {
        messages.tryEmit(UiMessage(text, undo))
    }

    fun outcome(o: Planner.Outcome) {
        when (o) {
            is Planner.Outcome.Message -> say(o.text, o.undo)
            is Planner.Outcome.Full -> say("${o.section.label} is full on that day.")
            Planner.Outcome.None -> {}
        }
    }

    /** Midnight reset; returns true if anything changed. Keeps the screen on "today" when the date rolls. */
    fun rollover(): Boolean {
        val before = planner.state.last
        val changed = planner.rollover()
        if (planner.state.last != before && viewDate == before) viewDate = planner.today()
        if (changed) commit()
        return changed
    }

    fun openDay(date: String) {
        tab = Tab.DAY
        viewDate = date
    }

    fun fullMessage(sec: Section) = say("${sec.title} is full.")
}
