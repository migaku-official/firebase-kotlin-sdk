package dev.gitlive.firebase.storage

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.FirebaseException
import kotlinx.coroutines.flow.Flow

expect val Firebase.storage: FirebaseStorage

expect fun Firebase.storage(app: FirebaseApp): FirebaseStorage

expect class FirebaseStorage {
    val reference: StorageReference
    fun reference(location: String): StorageReference
}

expect abstract class File

sealed class Progress(val bytesTransferred: Number, val totalByteCount: Number) {
    class Running internal constructor(bytesTransferred: Number, totalByteCount: Number): Progress(bytesTransferred, totalByteCount)
    class Paused internal constructor(bytesTransferred: Number, totalByteCount: Number): Progress(bytesTransferred, totalByteCount)
}

interface ProgressFlow : Flow<Progress> {
    fun pause(): Boolean
    fun resume(): Boolean
    fun cancel(): Boolean
}

expect class StorageReference {
    suspend fun getDownloadUrl(): String
    suspend fun putFile(file: File)
    fun putFileResumable(file: File): ProgressFlow
}

expect class FirebaseStorageException: FirebaseException

