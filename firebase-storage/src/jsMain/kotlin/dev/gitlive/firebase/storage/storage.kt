package dev.gitlive.firebase.storage

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.FirebaseException
import dev.gitlive.firebase.storage.externals.*
import dev.gitlive.firebase.storage.externals.FirebaseStorage
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.await

actual val Firebase.storage
    get() = FirebaseStorage(getStorage())

actual fun Firebase.storage(app: FirebaseApp)
        = FirebaseStorage(getStorage(app.js))

actual class FirebaseStorage internal constructor(val js: FirebaseStorage) {

    actual val reference get() = rethrow { StorageReference(ref(js)) }

    actual fun reference(location: String) = rethrow { StorageReference(ref(js, location)) }
}

actual abstract class File

actual class StorageReference internal constructor(val js: dev.gitlive.firebase.storage.externals.StorageReference) {

    actual suspend fun getDownloadUrl() = rethrow { getDownloadURL(js).await() }

    actual suspend fun putFile(file: File): Unit = rethrow { uploadBytes(js, file).await() }

    actual fun putFileResumable(file: File): ProgressFlow = rethrow {
    val js = uploadBytesResumable(js, file)

        val flow = callbackFlow {
            val unsubscribe = js.on(
                "state_changed",
                {
                    when(it.state) {
                        "paused" -> trySend(Progress.Paused(it.bytesTransferred, it.totalBytes))
                        "running" -> trySend(Progress.Running(it.bytesTransferred, it.totalBytes))
                        "canceled" -> cancel()
                        "success", "error" -> Unit
                        else -> TODO("Unknown state ${it.state}")
                    }
                },
                { close(errorToException(it)) },
                { close() }
            )
            awaitClose { unsubscribe() }
        }

        return object : ProgressFlow {
            override suspend fun collect(collector: FlowCollector<Progress>) = collector.emitAll(flow)
            override fun pause() = js.pause()
            override fun resume() = js.resume()
            override fun cancel() = js.cancel()
        }
    }
}

actual open class FirebaseStorageException(code: String, cause: Throwable) : FirebaseException(code, cause)

internal inline fun <R> rethrow(function: () -> R): R {
    try {
        return function()
    } catch (e: Exception) {
        throw e
    } catch (e: dynamic) {
        throw errorToException(e)
    }
}

internal fun errorToException(error: dynamic) = (error?.code ?: error?.message ?: "")
    .toString()
    .lowercase()
    .let { code ->
        when {
            else -> {
                println("Unknown error code in ${JSON.stringify(error)}")
                FirebaseStorageException(code, error)
            }
        }
    }
