package com.architectpsg.commandlibrary.feature.shell

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.architectpsg.commandlibrary.feature.shell.document.ContentResolverDocumentSource
import com.architectpsg.commandlibrary.feature.shell.document.DocumentSource
import com.architectpsg.commandlibrary.feature.shell.presentation.ShellControllers
import kotlinx.coroutines.Dispatchers

/** Survives configuration changes; clearing it cancels a running import through viewModelScope. */
internal class ShellViewModel(ports: ShellPorts, documents: DocumentSource) : ViewModel() {
    val controllers: ShellControllers = ShellControllers(viewModelScope, ports, documents, Dispatchers.IO)

    companion object {
        fun factory(ports: ShellPorts, resolver: ContentResolver): ViewModelProvider.Factory = viewModelFactory {
            initializer { ShellViewModel(ports, ContentResolverDocumentSource(resolver)) }
        }
    }
}
