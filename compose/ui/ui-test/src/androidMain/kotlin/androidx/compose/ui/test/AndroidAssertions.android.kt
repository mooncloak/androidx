/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.compose.ui.test

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.espresso.matcher.ViewMatchers

internal actual fun SemanticsNodeInteraction.checkIsDisplayed(
    assertIsFullyVisible: Boolean
): Boolean {
    val nodes = fetchSemanticsNodes(atLeastOneRootRequired = true)
    if (nodes.selectedNodes.isEmpty()) {
        // If the node doesn't exist, it's not displayed.
        return false
    }
    if (nodes.selectedNodes.size > 1) {
        throw AssertionError(
            "Failed to perform checkIsDisplayed check: Expected at most 1 node but found " +
                "${nodes.selectedNodes.size} nodes that satisfy (${selector.description})"
        )
    }
    val node = nodes.selectedNodes.single()

    fun isNotPlaced(node: LayoutInfo): Boolean {
        return !node.isPlaced
    }

    val layoutInfo = node.layoutInfo
    if (isNotPlaced(layoutInfo) || layoutInfo.findClosestParentNode(::isNotPlaced) != null) {
        return false
    }

    (node.root as? ViewRootForTest)?.let {
        if (!ViewMatchers.isDisplayed().matches(it.view)) {
            return false
        }
    }

    // check node doesn't clip unintentionally (e.g. row too small for content)
    val globalRect = node.boundsInWindow
    if (!node.isInScreenBounds(assertIsFullyVisible)) {
        return false
    }

    return (globalRect.width > 0f && globalRect.height > 0f)
}

internal actual fun SemanticsNode.clippedNodeBoundsInWindow(): Rect {
    val composeView = (root as ViewRootForTest).view
    val rootLocationInWindow =
        intArrayOf(0, 0).let {
            composeView.getLocationInWindow(it)
            Offset(it[0].toFloat(), it[1].toFloat())
        }
    return boundsInRoot.translate(rootLocationInWindow)
}

internal actual fun SemanticsNode.isInScreenBounds(assertIsFullyVisible: Boolean): Boolean {
    val composeView = (root as ViewRootForTest).view

    // Window relative bounds of our node
    val nodeBoundsInWindow = clippedNodeBoundsInWindow()
    if (nodeBoundsInWindow.width == 0f || nodeBoundsInWindow.height == 0f) {
        return false
    }

    // Window relative bounds of our compose root view that are visible on the screen
    val globalRootRect = android.graphics.Rect()
    if (!composeView.getGlobalVisibleRect(globalRootRect)) {
        return false
    }

    return if (assertIsFullyVisible) {
        // assertIsNotDisplayed only throws if the element is fully onscreen
        return nodeBoundsInWindow.top >= globalRootRect.top &&
            nodeBoundsInWindow.left >= globalRootRect.left &&
            nodeBoundsInWindow.right <= globalRootRect.right &&
            nodeBoundsInWindow.bottom <= globalRootRect.bottom
    } else {
        // assertIsDisplayed only throws if the element is fully offscreen
        !nodeBoundsInWindow
            .intersect(
                Rect(
                    globalRootRect.left.toFloat(),
                    globalRootRect.top.toFloat(),
                    globalRootRect.right.toFloat(),
                    globalRootRect.bottom.toFloat()
                )
            )
            .isEmpty
    }
}

/**
 * Executes [selector] on every parent of this [LayoutInfo] and returns the closest [LayoutInfo] to
 * return `true` from [selector] or null if [selector] returns false for all ancestors.
 */
private fun LayoutInfo.findClosestParentNode(selector: (LayoutInfo) -> Boolean): LayoutInfo? {
    var currentParent = this.parentInfo
    while (currentParent != null) {
        if (selector(currentParent)) {
            return currentParent
        } else {
            currentParent = currentParent.parentInfo
        }
    }

    return null
}
