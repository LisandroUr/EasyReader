package com.example.easyreader.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.easyreader.data.LibraryRepository

class LibrarySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): androidx.work.ListenableWorker.Result {
        return try {
            LibraryRepository(applicationContext).rescanAllBoundFolders()
            androidx.work.ListenableWorker.Result.success()
        } catch (_: Exception) {
            androidx.work.ListenableWorker.Result.retry()
        }
    }
}

