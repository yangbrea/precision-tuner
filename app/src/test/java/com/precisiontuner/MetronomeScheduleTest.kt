package com.precisiontuner

import com.precisiontuner.metronome.MetronomeSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeScheduleTest {

    private val RATE = 44100

    // ---- frame stepping ----

    @Test
    fun framesPerSubAt120BpmQuarterNotes() {
        val s = MetronomeSchedule(RATE, 120, 1, 4, true)
        assertEquals(22050L, s.framesPerSub)
    }

    @Test
    fun framesPerSubHalvesWithEighthSubdivision() {
        val s = MetronomeSchedule(RATE, 120, 2, 4, true)
        assertEquals(11025L, s.framesPerSub)
    }

    @Test
    fun framesPerSubAtExtremes() {
        assertEquals(88200L, MetronomeSchedule(RATE, 30, 1, 4, true).framesPerSub)
        assertEquals(2205L, MetronomeSchedule(RATE, 300, 4, 4, true).framesPerSub)
    }

    // ---- 4/4 quarter notes, accent on beat 1 ----

    @Test
    fun quarterNotesStepOneBeatAndWrapBeatNumbers() {
        val s = MetronomeSchedule(RATE, 120, 1, 4, true)
        val frames = (1..9).map { s.nextClick().startFrame }
        // First click was pre-queued at frame 0 (beat 1); next clicks step by one beat.
        assertEquals(listOf(22050L, 44100, 66150, 88200, 110250, 132300, 154350, 176400, 198450), frames)
        // Every click is a downbeat with no subdivisions.
        assertTrue(s.nextClick().downbeat)
        assertFalse(s.nextClick().subdivision)
    }

    @Test
    fun accentOnlyOnBeatOne() {
        val s = MetronomeSchedule(RATE, 120, 1, 4, true)
        val clicks = (1..9).map { s.nextClick() }
        // Beats: 2,3,4,1(next bar),2,3,4,1,2 — accent only when beat == 1.
        assertEquals(listOf(false, false, false, true, false, false, false, true, false), clicks.map { it.accent })
        assertEquals(listOf(88200L, 176400L), clicks.filter { it.accent }.map { it.startFrame })
    }

    @Test
    fun currentBeatTracksEmittedBeat() {
        val s = MetronomeSchedule(RATE, 120, 1, 4, true)
        // Pre-queued first click is beat 1; the schedule starts at beat 2.
        s.nextClick()
        assertEquals(2, s.currentBeat())
        s.nextClick()
        assertEquals(3, s.currentBeat())
        s.nextClick()
        assertEquals(4, s.currentBeat())
        s.nextClick() // wraps to beat 1 of the next bar
        assertEquals(1, s.currentBeat())
    }

    // ---- subdivisions ----

    @Test
    fun eighthNotesStepHalfBeatAndFlagSubdivisions() {
        val s = MetronomeSchedule(RATE, 120, 2, 4, true)
        val clicks = (1..8).map { s.nextClick() }
        // (beat1 sub1) (beat2 sub0) (beat2 sub1) (beat3 sub0) ...
        val expected = listOf(
            Triple(11025L, false, true),
            Triple(22050L, true, false),
            Triple(33075L, false, true),
            Triple(44100L, true, false),
            Triple(55125L, false, true),
            Triple(66150L, true, false),
            Triple(77175L, false, true),
            Triple(88200L, true, false),
        )
        assertEquals(expected, clicks.map { Triple(it.startFrame, it.downbeat, it.subdivision) })
        // Beat-1 downbeats land on bar starts and are accented.
        assertEquals(listOf(88200L), clicks.filter { it.accent }.map { it.startFrame })
    }

    @Test
    fun tripletsStepThirdOfBeat() {
        val s = MetronomeSchedule(RATE, 120, 3, 4, true)
        val frames = (1..6).map { s.nextClick().startFrame }
        assertEquals(listOf(7350L, 14700, 22050, 29400, 36750, 44100), frames)
        // Downbeats on every third click.
        val downbeats = (1..9).map { s.nextClick() }.filter { it.downbeat }
        assertEquals(listOf(66150L, 88200, 110250), downbeats.map { it.startFrame })
    }

    // ---- edge configurations ----

    @Test
    fun oneBeatPerBarAccentsEveryClick() {
        val s = MetronomeSchedule(RATE, 120, 1, 1, true)
        val clicks = (1..4).map { s.nextClick() }
        assertTrue(clicks.all { it.downbeat && it.accent && !it.subdivision })
        assertEquals(listOf(22050L, 44100, 66150, 88200), clicks.map { it.startFrame })
    }

    @Test
    fun accentDisabledNeverAccents() {
        val s = MetronomeSchedule(RATE, 120, 1, 4, false)
        val clicks = (1..9).map { s.nextClick() }
        assertFalse(clicks.any { it.accent })
    }

    // ---- live updates ----

    @Test
    fun updateKeepsBeatStateAndAppliesNewFrameStep() {
        val s = MetronomeSchedule(RATE, 120, 1, 4, true)
        val first = s.nextClick() // beat 2 at 22050
        assertEquals(22050L, first.startFrame)
        s.update(240, 1, 4, true)
        assertEquals(11025L, s.framesPerSub)
        val second = s.nextClick() // beat 3, step now half a beat
        assertEquals(22050L + 11025, second.startFrame)
        assertEquals(3, s.currentBeat())
        // A smaller subdivision while running wraps cleanly instead of hanging.
        s.update(120, 1, 4, true)
        val third = s.nextClick()
        assertEquals(33075L + 22050, third.startFrame)
        assertTrue(third.downbeat)
    }

    @Test
    fun subdivisionShrinkWrapsCleanly() {
        val s = MetronomeSchedule(RATE, 120, 4, 4, true)
        val subStep = s.framesPerSub // 5512 (44100*60/(120*4), truncated)
        repeat(2) { s.nextClick() } // emitted beat1 sub1, sub2; next would be sub3
        s.update(120, 1, 4, true) // collapse to quarter notes mid-beat
        val click = s.nextClick()
        assertTrue("should wrap to the next downbeat", click.downbeat)
        assertEquals(2 * subStep + 22050, click.startFrame)
    }
}
