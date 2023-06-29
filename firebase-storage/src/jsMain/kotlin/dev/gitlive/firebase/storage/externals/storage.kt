@file:JsModule("firebase/storage")
@file:JsNonModule

package dev.gitlive.firebase.storage.externals

import dev.gitlive.firebase.externals.FirebaseApp
import kotlin.js.Promise

external fun getStorage(app: FirebaseApp? = definedExternally): FirebaseStorage

external fun ref(storage: FirebaseStorage, url: String? = definedExternally): StorageReference

external fun getDownloadURL(ref: StorageReference): Promise<String>

external fun uploadBytes(ref: StorageReference, file: dynamic): Promise<Unit>

external fun  uploadBytesResumable(ref: StorageReference, data: dynamic): UploadTask

external interface FirebaseStorage

external interface StorageReference

external interface StorageError

external interface UploadTaskSnapshot {
    val bytesTransferred: Number
    val ref: StorageReference
    val state: String
    val task: UploadTask
    val totalBytes: Number
}

external class UploadTask : Promise<UploadTaskSnapshot> {
    fun cancel(): Boolean;
    fun on(event: String, next: (snapshot: UploadTaskSnapshot) -> Unit, error: (a: StorageError) -> Unit, complete: () -> Unit): () -> Unit
    fun pause(): Boolean;
    fun resume(): Boolean;
    val snapshot: UploadTaskSnapshot
}
