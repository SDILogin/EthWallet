package me.sdi.ethwallet.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

private const val ARTIFACTS_FILE_NAME = "secure_pin_preferences.preferences_pb"
private const val POSIX_VIEW = "posix"
private val OWNER_ONLY_DIRECTORY = PosixFilePermissions.fromString("rwx------")

/**
 * Opens the preferences store holding the PIN verifier and master KEK inside [directory], which is created (or
 * tightened) so that only its owner may enter it.
 *
 * The protection has to sit on the directory: DataStore writes through a temporary file created
 * under the process umask and renamed over the target, so any mode set on the stored file itself is
 * dropped by the next write. A directory no other user can traverse keeps both files unreachable.
 * Filesystems without POSIX permissions, such as Windows, get the plain directory.
 *
 * Only one store may be open per file per process, so the returned instance must be shared by every
 * reader and writer of the artifacts.
 */
fun pinArtifactsDataStore(directory: File): DataStore<Preferences> {
    val path = directory.toPath().toAbsolutePath()
    if (FileSystems.getDefault().supportedFileAttributeViews().contains(POSIX_VIEW)) {
        Files.createDirectories(path, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY))
        Files.setPosixFilePermissions(path, OWNER_ONLY_DIRECTORY)
    } else {
        Files.createDirectories(path)
    }

    val artifactsFile = path.resolve(ARTIFACTS_FILE_NAME)
    return PreferenceDataStoreFactory.createWithPath { artifactsFile.toString().toPath() }
}
