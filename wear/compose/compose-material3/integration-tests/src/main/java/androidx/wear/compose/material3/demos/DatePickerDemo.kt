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

package androidx.wear.compose.material3.demos

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.integration.demos.common.ComposableDemo
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.DatePicker
import androidx.wear.compose.material3.DatePickerType
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.samples.DatePickerMinDateMaxDateSample
import androidx.wear.compose.material3.samples.DatePickerSample
import androidx.wear.compose.material3.samples.DatePickerYearMonthDaySample
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
val DatePickerDemos =
    listOf(
        ComposableDemo("Date Year-Month-Day") { DatePickerYearMonthDaySample() },
        ComposableDemo("Date Month-Day-Year") { DatePickerDemo(DatePickerType.MonthDayYear) },
        ComposableDemo("Date Day-Month-Year") { DatePickerDemo(DatePickerType.DayMonthYear) },
        ComposableDemo("Date System date format") { DatePickerSample() },
        ComposableDemo("Date Range") { DatePickerMinDateMaxDateSample() },
    )

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DatePickerDemo(datePickerType: DatePickerType) {
    var showDatePicker by remember { mutableStateOf(true) }
    var datePickerDate by remember { mutableStateOf(LocalDate.now()) }
    val formatter =
        when (datePickerType) {
            DatePickerType.MonthDayYear -> "MMM d yyyy"
            DatePickerType.DayMonthYear -> "d MMM yyyy"
            else -> "yyyy MMM d"
        }.let { DateTimeFormatter.ofPattern(it) }
    if (showDatePicker) {
        DatePicker(
            initialDate = datePickerDate, // Initialize with last picked date on reopen
            onDatePicked = {
                datePickerDate = it
                showDatePicker = false
            },
            datePickerType = datePickerType
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = { showDatePicker = true },
                label = { Text("Selected Date") },
                secondaryLabel = { Text(datePickerDate.format(formatter)) },
                icon = { Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit") },
            )
        }
    }
}
