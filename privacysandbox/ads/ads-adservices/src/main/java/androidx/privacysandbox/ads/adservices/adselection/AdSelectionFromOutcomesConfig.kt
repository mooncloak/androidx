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

package androidx.privacysandbox.ads.adservices.adselection

import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.annotation.RequiresExtension
import androidx.annotation.RestrictTo
import androidx.privacysandbox.ads.adservices.common.AdSelectionSignals
import androidx.privacysandbox.ads.adservices.common.AdTechIdentifier
import androidx.privacysandbox.ads.adservices.common.ExperimentalFeatures

/**
 * Contains the configuration of the ad selection process that select a winner from a given list of
 * ad selection ids.
 *
 * Instances of this class are created by SDKs to be provided as arguments to the
 * [AdSelectionManager#selectAds] methods in [AdSelectionManager].
 *
 * @param seller AdTechIdentifier of the seller, for example "www.example-ssp.com".
 * @param adSelectionIds a list of ad selection ids passed by the SSP to participate in the ad
 *   selection from outcomes process.
 * @param adSelectionSignals signals given to the participating buyers in the ad selection and
 *   reporting processes.
 * @param selectionLogicUri the URI used to retrieve the JS code containing the seller/SSP function
 *   used during the ad selection.
 */
@ExperimentalFeatures.Ext10OptIn
class AdSelectionFromOutcomesConfig
public constructor(
    val seller: AdTechIdentifier,
    val adSelectionIds: List<Long>,
    val adSelectionSignals: AdSelectionSignals,
    var selectionLogicUri: Uri,
) {

    /** Checks whether two [AdSelectionFromOutcomesConfig] objects contain the same information. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdSelectionFromOutcomesConfig) return false
        return this.seller == other.seller &&
            this.adSelectionIds == other.adSelectionIds &&
            this.adSelectionSignals == other.adSelectionSignals &&
            this.selectionLogicUri == other.selectionLogicUri
    }

    /** Returns the hash of the [AdSelectionFromOutcomesConfig] object's data. */
    override fun hashCode(): Int {
        var hash = seller.hashCode()
        hash = 31 * hash + adSelectionIds.hashCode()
        hash = 31 * hash + adSelectionSignals.hashCode()
        hash = 31 * hash + selectionLogicUri.hashCode()
        return hash
    }

    /** Overrides the toString method. */
    override fun toString(): String {
        return "AdSelectionFromOutcomesConfig: seller=$seller, adSelectionIds='$adSelectionIds', " +
            "adSelectionSignals=$adSelectionSignals, selectionLogicUri=$selectionLogicUri"
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @RequiresExtension(extension = SdkExtensions.AD_SERVICES, version = 10)
    @RequiresExtension(extension = Build.VERSION_CODES.S, version = 10)
    internal fun convertToAdServices():
        android.adservices.adselection.AdSelectionFromOutcomesConfig {
        return android.adservices.adselection.AdSelectionFromOutcomesConfig.Builder()
            .setSelectionSignals(adSelectionSignals.convertToAdServices())
            .setAdSelectionIds(adSelectionIds)
            .setSelectionLogicUri(selectionLogicUri)
            .setSeller(seller.convertToAdServices())
            .build()
    }
}
