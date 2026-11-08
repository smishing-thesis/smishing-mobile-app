package com.app.smishing.domain.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsRoleStateTest {

    @Test
    fun roleHeldWhenAvailableAndHeld() {
        assertEquals(SmsRoleState.Held, smsRoleState(roleAvailable = true, roleHeld = true))
    }

    @Test
    fun roleNotHeldWhenAvailableButNotHeld() {
        assertEquals(SmsRoleState.NotHeld, smsRoleState(roleAvailable = true, roleHeld = false))
    }

    @Test
    fun roleUnavailableWhenThePhoneDoesNotOfferIt() {
        assertEquals(SmsRoleState.Unavailable, smsRoleState(roleAvailable = false, roleHeld = false))
    }

    @Test
    fun unavailableWinsEvenIfTheSystemReportsItHeld() {
        assertEquals(SmsRoleState.Unavailable, smsRoleState(roleAvailable = false, roleHeld = true))
    }
}
