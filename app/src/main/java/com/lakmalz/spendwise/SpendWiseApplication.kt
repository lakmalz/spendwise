package com.lakmalz.spendwise

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

// Configuration.Provider disables WorkManager's default initializer so we can
// supply our own HiltWorkerFactory — required for @HiltWorker to work.
@HiltAndroidApp
class SpendWiseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
