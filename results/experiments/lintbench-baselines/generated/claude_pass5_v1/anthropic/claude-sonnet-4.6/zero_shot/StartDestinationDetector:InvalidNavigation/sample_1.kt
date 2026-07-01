/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Detector that checks that all `<navigation>` elements have a valid
 * `app:startDestination` attribute pointing to a direct child.
 */
class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"

        @JvmField
        val ISSUE = Issue.create(
            id = "InvalidNavigation",
            briefDescription = "No start destination specified",
            explanation = """
                All `<navigation>` elements must have a start destination specified, \
                and it must be a direct child of that `<navigation>`.
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                StartDestinationDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Check if startDestination attribute is present
        val startDestination = element.getAttributeNS(AUTO_URI, ATTR_START_DESTINATION)
            .takeIf { it.isNotEmpty() }
            ?: run {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "No start destination specified"
                )
                return
            }

        // Resolve the reference: strip leading @[+]id/ or @[+]android:id/ prefix
        val referencedId = stripIdPrefix(startDestination)
        if (referencedId == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "No start destination specified"
            )
            return
        }

        // Collect ids of direct children
        val childNodes = element.childNodes
        var foundChild = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child !is Element) continue

            // Check android:id and app:id on the child
            val androidId = child.getAttributeNS(ANDROID_URI, ATTR_ID)
                .takeIf { it.isNotEmpty() }
                ?.let { stripIdPrefix(it) }
            val autoId = child.getAttributeNS(AUTO_URI, ATTR_ID)
                .takeIf { it.isNotEmpty() }
                ?.let { stripIdPrefix(it) }

            if (androidId == referencedId || autoId == referencedId) {
                foundChild = true
                break
            }
        }

        if (!foundChild) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "Start destination `$startDestination` is not a direct child of this " +
                    "`<$TAG_NAVIGATION>`"
            )
        }
    }

    /**
     * Strips the `@[+]id/`, `@[+]android:id/`, or similar prefix from a
     * resource reference and returns the bare name, or null if the format
     * is not recognised.
     */
    private fun stripIdPrefix(reference: String): String? {
        // Matches @id/foo, @+id/foo, @android:id/foo, @+android:id/foo, etc.
        val regex = Regex("""^@\+?(?:\w+:)?id/(.+)$""")
        return regex.matchEntire(reference)?.groupValues?.get(1)
    }
}