package io.github.halilkhrmn.dpimech.engine

import android.content.Context
import io.github.halilkhrmn.dpimech.core.EngineLauncher

/** Strategy Lab engines: a short-lived ciadpi process per strategy, like the profile's own. */
class AndroidEngineLauncher(private val context: Context) : EngineLauncher {
    override fun start(args: List<String>, port: Int): AutoCloseable {
        val engine = Ciadpi.start(context, args, port, "lab:$port")
        return AutoCloseable { engine.stop() }
    }
}
