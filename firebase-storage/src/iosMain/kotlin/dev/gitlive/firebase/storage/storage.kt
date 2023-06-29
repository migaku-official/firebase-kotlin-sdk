package dev.gitlive.firebase.storage

import cocoapods.FirebaseStorage.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseApp
import dev.gitlive.firebase.FirebaseException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import platform.Foundation.*

actual val Firebase.storage
    get() = FirebaseStorage(FIRStorage.storage())

actual fun Firebase.storage(app: FirebaseApp) : FirebaseStorage = TODO("Come back to issue")
//        = FirebaseStorage(FIRStorage.storageWithApp(app.ios))

actual class FirebaseStorage internal constructor(val ios: FIRStorage) {

    actual val reference get() = StorageReference(ios.reference())

    actual fun reference(location: String) = StorageReference(ios.referenceWithPath(location))
}

actual class File(val url: NSURL)

actual class StorageReference internal constructor(val ios: FIRStorageReference) {

    actual suspend fun getDownloadUrl() = ios.awaitResult { downloadURLWithCompletion(completion = it) }.absoluteString()!!

    actual suspend fun putFile(file: File) = ios.awaitResult { putFile(file.url, null, completion = it) }.run {}

    actual fun putFileResumable(file: File): ProgressFlow {
        val ios = ios.putFile(file.url)

        val flow = callbackFlow {
            ios.observeStatus(FIRStorageTaskStatusProgress) {
                val progress = it!!.progress()!!
                trySendBlocking(Progress.Running(progress.completedUnitCount, progress.totalUnitCount))
            }
            ios.observeStatus(FIRStorageTaskStatusPause) {
                val progress = it!!.progress()!!
                trySendBlocking(Progress.Paused(progress.completedUnitCount, progress.totalUnitCount))
            }
            ios.observeStatus(FIRStorageTaskStatusResume) {
                val progress = it!!.progress()!!
                trySendBlocking(Progress.Running(progress.completedUnitCount, progress.totalUnitCount))
            }
            ios.observeStatus(FIRStorageTaskStatusSuccess) { close(FirebaseStorageException(it!!.error().toString())) }
            ios.observeStatus(FIRStorageTaskStatusFailure) {
                when(it!!.error()!!.code) {
                    /*FIRStorageErrorCodeCancelled = */ -13040L -> cancel(it.error()!!.localizedDescription)
                    else -> close(FirebaseStorageException(it.error().toString()))
                }
            }
            awaitClose { ios.removeAllObservers() }
        }

        return object : ProgressFlow {
            override suspend fun collect(collector: FlowCollector<Progress>) = collector.emitAll(flow)
            override fun pause() = ios.pause()
            override fun resume() = ios.resume()
            override fun cancel() = ios.cancel()
        }
    }
}

actual class FirebaseStorageException(message: String): FirebaseException(message)

suspend inline fun <T> T.await(function: T.(callback: (NSError?) -> Unit) -> Unit) {
    val job = CompletableDeferred<Unit>()
    function { error ->
        if(error == null) {
            job.complete(Unit)
        } else {
            job.completeExceptionally(FirebaseStorageException(error.toString()))
        }
    }
    job.await()
}

suspend inline fun <T, reified R> T.awaitResult(function: T.(callback: (R?, NSError?) -> Unit) -> Unit): R {
    val job = CompletableDeferred<R?>()
    function { result, error ->
        if(error == null) {
            job.complete(result)
        } else {
            job.completeExceptionally(FirebaseStorageException(error.toString()))
        }
    }
    return job.await() as R
}
