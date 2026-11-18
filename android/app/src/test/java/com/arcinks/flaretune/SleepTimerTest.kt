package com.arcinks.flaretune

import com.arcinks.flaretune.playback.PlaybackSleepTimer
import com.arcinks.flaretune.playback.SleepTimerMode
import com.arcinks.flaretune.playback.SleepTimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    @Test fun timedDeadlineIsStaticAndExpiryFadesPausesThenRestoresGain() = runTest {
        val f = fixture()
        val states = mutableListOf<SleepTimerState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { f.timer.state.collect { states += it } }
        f.timer.setSleepTimer(1)
        assertEquals(SleepTimerState(SleepTimerMode.TIMED, 60_000), f.timer.state.value)
        assertEquals(65_600L, f.wakeTimeout)
        advanceTimeBy(59_999); runCurrent()
        assertEquals(2, states.size)
        assertEquals(0, f.target.pauses)
        assertEquals(0.75f, f.target.volume, 0f)
        advanceTimeBy(301); runCurrent()
        assertTrue(f.timer.isFading)
        assertTrue(f.target.volume in 0.01f..0.74f)
        assertEquals(0.75f, f.timer.displayedVolume!!, 0f)
        assertEquals(2, states.size)
        advanceTimeBy(300); runCurrent()
        assertEquals(1, f.target.pauses)
        assertEquals(0f, f.target.gainAtPause, 0f)
        assertEquals(0.75f, f.target.volume, 0f)
        assertFalse(f.timer.isFading)
        assertEquals(SleepTimerState(), f.timer.state.value)
        assertFalse(f.awake)
        assertEquals(3, states.size)
    }

    @Test fun cancellingBeforeDeadlineCannotPauseLater() = runTest {
        val f = fixture()
        f.timer.setSleepTimer(1)
        advanceTimeBy(30_000)
        f.timer.cancel()
        assertFalse(f.awake)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, f.target.pauses)
        assertEquals(SleepTimerMode.OFF, f.timer.state.value.mode)
    }

    @Test fun cancellationDuringFadeImmediatelyRestoresGainAndRejectsLatePause() = runTest {
        val f = fixture()
        f.timer.setSleepTimer(1)
        advanceTimeBy(60_300); runCurrent()
        assertTrue(f.target.volume < 0.75f)
        f.timer.cancel()
        assertEquals(0.75f, f.target.volume, 0f)
        assertFalse(f.timer.isFading)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, f.target.pauses)
        assertEquals(0.75f, f.target.volume, 0f)
    }

    @Test fun replacementTimerRejectsPreviousDeadlineAndPublishesOnlyNewDeadline() = runTest {
        val f = fixture()
        f.timer.setSleepTimer(1)
        advanceTimeBy(30_000)
        f.timer.setSleepTimer(2)
        assertEquals(150_000L, f.timer.state.value.endElapsedRealtimeMs)
        advanceTimeBy(31_000); runCurrent()
        assertEquals(0, f.target.pauses)
        assertEquals(SleepTimerMode.TIMED, f.timer.state.value.mode)
        advanceTimeBy(90_000); runCurrent()
        assertEquals(1, f.target.pauses)
        assertEquals(SleepTimerMode.OFF, f.timer.state.value.mode)
    }

    @Test fun endOfTrackArmsNativeBoundaryFadesLastPartAndResetsOff() = runTest {
        val f = fixture()
        f.timer.setSleepTimerEndOfTrack()
        assertTrue(f.target.pauseAtEndOfTrack)
        assertEquals(SleepTimerState(SleepTimerMode.END_OF_TRACK), f.timer.state.value)
        f.timer.observeTrack(f.owner, 39_500, 40_000, playing = true)
        advanceTimeBy(500); runCurrent()
        assertEquals(1, f.target.pauses)
        assertEquals(0.75f, f.target.volume, 0f)
        assertFalse(f.target.pauseAtEndOfTrack)
        assertEquals(SleepTimerMode.OFF, f.timer.state.value.mode)
    }

    @Test fun unknownDurationUsesNativeTrackEndAndStillResetsMode() = runTest {
        val f = fixture()
        f.timer.setSleepTimerEndOfTrack()
        f.timer.observeTrack(f.owner, 5_000, 0, playing = true)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(0, f.target.pauses)
        f.timer.onTrackEnded(f.owner)
        assertEquals(1, f.target.pauses)
        assertFalse(f.target.pauseAtEndOfTrack)
        assertEquals(SleepTimerMode.OFF, f.timer.state.value.mode)
    }

    @Test fun cancellingEndOfTrackRemovesNativeGuard() = runTest {
        val f = fixture()
        f.timer.setSleepTimerEndOfTrack()
        f.timer.cancel()
        assertFalse(f.target.pauseAtEndOfTrack)
        f.timer.onTrackEnded(f.owner)
        assertEquals(0, f.target.pauses)
    }

    @Test fun switchingServiceCancelsTimerAndOldServiceCannotAffectNewOwner() = runTest {
        val f = fixture()
        f.timer.setSleepTimer(1)
        val nextOwner = Any()
        val nextPlayer = Target()
        f.timer.attach(nextOwner, backgroundScope, nextPlayer, { testScheduler.currentTime })
        assertFalse(f.awake)
        assertEquals(SleepTimerMode.OFF, f.timer.state.value.mode)
        f.timer.setSleepTimerEndOfTrack()
        f.timer.detach(f.owner)
        f.timer.onTrackEnded(f.owner)
        assertTrue(nextPlayer.pauseAtEndOfTrack)
        assertEquals(0, nextPlayer.pauses)
        advanceTimeBy(70_000); runCurrent()
        assertEquals(0, f.target.pauses)
        f.timer.detach(nextOwner)
        assertEquals(SleepTimerMode.OFF, f.timer.state.value.mode)
        assertFalse(nextPlayer.pauseAtEndOfTrack)
    }

    @Test fun timedTimerWithFinishCurrentTrackTransitionsToEndOfTrackAtDeadline() = runTest {
        val f = fixture()
        f.timer.setSleepTimer(1)
        f.timer.setSleepTimerFinishCurrentTrack(true)
        assertTrue(f.timer.state.value.stopAfterCurrentTrack)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(0, f.target.pauses)
        assertTrue(f.target.pauseAtEndOfTrack)
        assertEquals(SleepTimerMode.END_OF_TRACK, f.timer.state.value.mode)
        assertTrue(f.timer.state.value.stopAfterCurrentTrack)
        f.timer.onTrackEnded(f.owner)
        assertEquals(1, f.target.pauses)
        assertFalse(f.target.pauseAtEndOfTrack)
        assertEquals(SleepTimerMode.OFF, f.timer.state.value.mode)
        assertFalse(f.timer.state.value.stopAfterCurrentTrack)
    }

    @Test fun disablingFinishCurrentTrackDuringTimedTimerRestoresNormalDeadlinePause() = runTest {
        val f = fixture()
        f.timer.setSleepTimerFinishCurrentTrack(true)
        assertEquals(SleepTimerMode.END_OF_TRACK, f.timer.state.value.mode)
        f.timer.setSleepTimer(1)
        assertEquals(SleepTimerMode.TIMED, f.timer.state.value.mode)
        assertTrue(f.timer.state.value.finishCurrentTrackAfterTimer)
        f.timer.setSleepTimerFinishCurrentTrack(false)
        assertEquals(SleepTimerMode.TIMED, f.timer.state.value.mode)
        assertFalse(f.timer.state.value.finishCurrentTrackAfterTimer)
        advanceTimeBy(60_600); runCurrent()
        assertEquals(1, f.target.pauses)
        assertEquals(SleepTimerMode.OFF, f.timer.state.value.mode)
    }

    private fun TestScope.fixture(): Fixture = Fixture().also { f ->
        f.timer.attach(f.owner, backgroundScope, f.target, { testScheduler.currentTime },
            { f.awake = true; f.wakeTimeout = it }, { f.awake = false })
    }

    private class Fixture {
        val owner = Any()
        val timer = PlaybackSleepTimer()
        val target = Target()
        var awake = false
        var wakeTimeout = 0L
    }

    private class Target : PlaybackSleepTimer.Target {
        override var volume = 0.75f
        override var pauseAtEndOfTrack = false
        var pauses = 0
        var gainAtPause = -1f
        override fun pause() { pauses++; gainAtPause = volume }
    }
}
