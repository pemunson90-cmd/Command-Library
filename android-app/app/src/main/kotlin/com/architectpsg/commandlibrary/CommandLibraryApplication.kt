package com.architectpsg.commandlibrary

import android.app.Application
import com.architectpsg.commandlibrary.di.AppGraph

/** Owns the single process-wide [AppGraph]. */
public class CommandLibraryApplication : Application() {
    internal val graph: AppGraph by lazy { AppGraph(this) }
}
