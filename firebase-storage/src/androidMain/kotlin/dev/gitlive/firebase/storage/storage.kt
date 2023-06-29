package dev.gitlive.firebase.storage

import android.net.Uri
import com.google.android.gms.tasks.OnCanceledListener
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.storage.OnPausedListener
import com.google.firebase.storage.OnProgressListener
import com.google.firebase.storage.UploadTask
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.tasks.await

actual val Firebase.storage
    get() = FirebaseStorage(com.google.firebase.storage.FirebaseStorage.getInstance())

actual fun Firebase.storage(app: FirebaseApp)
        = FirebaseStorage(com.google.firebase.storage.FirebaseStorage.getInstance(app.android))

actual class FirebaseStorage internal constructor(val android: com.google.firebase.storage.FirebaseStorage) {

    actual val reference get() = StorageReference(android.reference)

    actual fun reference(location: String )= StorageReference(android.getReference(location))
}

actual class File(val uri: Uri)

actual class StorageReference internal constructor(val android: com.google.firebase.storage.StorageReference) {

    actual suspend fun getDownloadUrl() = android.downloadUrl.await().toString()

    actual suspend fun putFile(file: File) = android.putFile(file.uri).await().run {}

    actual fun putFileResumable(file: File): ProgressFlow {
        val android = android.putFile(file.uri)

        val flow = callbackFlow {
            val onCanceledListener = OnCanceledListener { cancel() }
            val onCompleteListener = OnCompleteListener<UploadTask.TaskSnapshot> { close(it.exception) }
            val onPausedListener = OnPausedListener<UploadTask.TaskSnapshot> { trySendBlocking(Progress.Paused(it.bytesTransferred, it.totalByteCount)) }
            val onProgressListener = OnProgressListener<UploadTask.TaskSnapshot> { trySendBlocking(Progress.Running(it.bytesTransferred, it.totalByteCount)) }
            android.addOnCanceledListener(onCanceledListener)
            android.addOnCompleteListener(onCompleteListener)
            android.addOnPausedListener(onPausedListener)
            android.addOnProgressListener(onProgressListener)
            awaitClose {
                android.removeOnCanceledListener(onCanceledListener)
                android.removeOnCompleteListener(onCompleteListener)
                android.removeOnPausedListener(onPausedListener)
                android.removeOnProgressListener(onProgressListener)
            }
        }

        return object : ProgressFlow {
            override suspend fun collect(collector: FlowCollector<Progress>) = collector.emitAll(flow)
            override fun pause() = android.pause().run {}
            override fun resume() = android.resume().run {}
            override fun cancel() = android.cancel().run {}
        }
    }
}

actual typealias FirebaseStorageException = com.google.firebase.storage.StorageException
