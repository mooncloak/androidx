/*
 * Copyright (C) 2019 The Android Open Source Project
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

plugins {
    kotlin("jvm") version "2.0.20" apply false
    kotlin("multiplatform") version "2.0.20" apply false
    kotlin("plugin.serialization") version "2.0.20" apply false
    id("com.android.library") version "8.2.2" apply false
    id("com.android.application") version "8.2.2" apply false
    //id("org.jetbrains.dokka")
    //id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("org.jetbrains.compose") version "1.7.0-rc01" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false
    id("androidx.variables")
}

group = buildVariables.group
version = buildVariables.version

