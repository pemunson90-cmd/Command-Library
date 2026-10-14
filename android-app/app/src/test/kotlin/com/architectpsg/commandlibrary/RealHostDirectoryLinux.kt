package com.architectpsg.commandlibrary

import android.system.ErrnoException
import android.system.OsConstants
import android.system.StructStat
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLinux

/** Real host lstat/directory-fd/fsync for the scoped factory tests, never packaged on Android.
 * Java NIO exposes no block-accounting values; those unused StructStat fields remain zero.
 */
@Implements(className = "libcore.io.Linux", isInAndroidSdk = false)
class RealHostDirectoryLinux : ShadowLinux() {
    private data class Directory(val channel: FileChannel, val number: Int)
    private val directories = ConcurrentHashMap<FileDescriptor, Directory>()
    private val closed = ConcurrentHashMap.newKeySet<FileDescriptor>()

    @Implementation
    public override fun lstat(path: String): StructStat = hostStat(Paths.get(path), true, "lstat")

    @Implementation
    public override fun open(path: String, flags: Int, mode: Int): FileDescriptor {
        val stat = hostStat(Paths.get(path), true, "open")
        if (!OsConstants.S_ISDIR(stat.st_mode)) return super.open(path, flags, mode)
        if (flags != OsConstants.O_RDONLY) throw ErrnoException("open", OsConstants.EINVAL)
        return hostCall("open") {
            val channel = FileChannel.open(Paths.get(path), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)
            try {
                val descriptorField = channel.javaClass.getDeclaredField("fd").apply { isAccessible = true }
                val descriptor = descriptorField.get(channel) as FileDescriptor
                val numberField = FileDescriptor::class.java.getDeclaredField("fd").apply { isAccessible = true }
                directories[descriptor] = Directory(channel, numberField.getInt(descriptor))
                descriptor
            } catch (failure: Throwable) {
                try { channel.close() } catch (close: Throwable) { failure.addSuppressed(close) }
                throw failure
            }
        }
    }

    @Implementation
    public override fun fstat(descriptor: FileDescriptor): StructStat {
        if (descriptor in closed) throw ErrnoException("fstat", OsConstants.EBADF)
        val directory = directories[descriptor] ?: return super.fstat(descriptor)
        // Follow /proc's descriptor link, so renaming/replacing the original path cannot fake mode.
        return hostStat(Paths.get("/proc/self/fd/${directory.number}"), false, "fstat")
    }

    @Implementation
    fun fsync(descriptor: FileDescriptor) {
        if (descriptor in closed) throw ErrnoException("fsync", OsConstants.EBADF)
        hostCall("fsync") {
            val directory = directories[descriptor]
            if (directory != null) directory.channel.force(true) else descriptor.sync()
        }
    }

    @Implementation
    fun close(descriptor: FileDescriptor) {
        if (!closed.add(descriptor)) throw ErrnoException("close", OsConstants.EBADF)
        hostCall("close") {
            val directory = directories.remove(descriptor)
            if (directory != null) directory.channel.close() else FileInputStream(descriptor).close()
        }
    }

    private fun hostStat(path: Path, noFollow: Boolean, call: String): StructStat = hostCall(call) {
        val attributes = if (noFollow) Files.readAttributes(path, "unix:*", LinkOption.NOFOLLOW_LINKS)
            else Files.readAttributes(path, "unix:*")
        fun number(key: String) = (attributes.getValue(key) as Number).toLong()
        fun time(key: String) = (attributes.getValue(key) as FileTime).to(TimeUnit.SECONDS)
        StructStat(number("dev"), number("ino"), number("mode").toInt(), number("nlink"), number("uid").toInt(), number("gid").toInt(),
            number("rdev"), number("size"), time("lastAccessTime"), time("lastModifiedTime"), time("ctime"), 0, 0)
    }

    private fun <T> hostCall(call: String, block: () -> T): T = try { block() }
    catch (failure: NoSuchFileException) { throw ErrnoException(call, if (call == "fstat") OsConstants.EBADF else OsConstants.ENOENT, failure) }
    catch (failure: AccessDeniedException) { throw ErrnoException(call, OsConstants.EACCES, failure) }
    catch (failure: SecurityException) { throw ErrnoException(call, OsConstants.EACCES, failure) }
    catch (failure: IOException) { throw ErrnoException(call, OsConstants.EIO, failure) }
}
