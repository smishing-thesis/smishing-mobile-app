package com.app.smishing.data.sms

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import com.app.smishing.domain.sms.SmsRoleRepository
import com.app.smishing.domain.sms.SmsRoleState
import com.app.smishing.domain.sms.smsRoleState

/** Estado y solicitud del rol de SMS con `RoleManager` (Android 10 o superior, el mínimo de la app). */
class AndroidSmsRoleRepository(context: Context) : SmsRoleRepository {

    private val roleManager: RoleManager? = context.getSystemService(RoleManager::class.java)

    override fun state(): SmsRoleState {
        val manager = roleManager ?: return SmsRoleState.Unavailable
        return smsRoleState(
            roleAvailable = manager.isRoleAvailable(RoleManager.ROLE_SMS),
            roleHeld = manager.isRoleHeld(RoleManager.ROLE_SMS),
        )
    }

    /** Intent del diálogo del sistema que pide el rol, o nulo si el teléfono no lo ofrece. */
    fun requestIntent(): Intent? {
        val manager = roleManager ?: return null
        return if (manager.isRoleAvailable(RoleManager.ROLE_SMS)) manager.createRequestRoleIntent(RoleManager.ROLE_SMS) else null
    }
}
