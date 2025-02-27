/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.wear.compose.material3.macrobenchmark.common.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable

val BaselineProfileScreens =
    listOf(
        AlertDialogScreen,
        TextToggleButtonScreen,
        AnimatedTextScreen,
        ButtonGroupScreen,
        ButtonScreen,
        CardScreen,
        CheckboxButtonScreen,
        ColorSchemeScreen,
        ConfirmationScreen,
        CurvedTextScreen,
        DatePickerScreen,
        EdgeButtonScreen,
        IconButtonScreen,
        IconToggleButtonScreen,
        ListHeaderScreen,
        OpenOnPhoneDialogScreen,
        PageIndicatorScreen,
        PickerGroupScreen,
        PickerScreen,
        PlaceHolderScreen,
        ProgressIndicatorScreen,
        RadioButtonScreen,
        ScaffoldScreen,
        ScrollIndicatorScreen,
        SwipeToDismissScreen,
        SliderScreen,
        StepperScreen,
        SwipeToRevealScreen,
        SwitchButtonScreen,
        TextButtonScreen,
        TimeTextScreen,
        TimePickerScreen,
        TransformingLazyColumnScreen,
    )

/** Represents a screen used for generating a baseline profile. */
interface BaselineProfileScreen {
    val content: @Composable BoxScope.() -> Unit
    val exercise: MacrobenchmarkScope.() -> Unit
        get() = { device.waitForIdle() }
}
