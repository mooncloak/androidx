/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.impl.utils.executor

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

/**
 * Helper class for retrieving an [ScheduledExecutorService] which will post to the main thread.
 *
 * Since [ScheduledExecutorService] implements [Executor], this can also be used as a simple
 * Executor.
 */
internal class MainThreadExecutor {
    companion object {
        val instance: ScheduledExecutorService by lazy {
            HandlerScheduledExecutorService(Handler(Looper.getMainLooper()))
        }
    }
}
