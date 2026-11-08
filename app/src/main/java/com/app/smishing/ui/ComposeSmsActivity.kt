package com.app.smishing.ui

import android.os.Bundle
import androidx.activity.ComponentActivity

/** Recibe los enlaces `sms:` para redactar un mensaje. Android exige declararlo para el rol de SMS (#2); redactar es #6. */
class ComposeSmsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
