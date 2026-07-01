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
 * `app:startDestination` attribute that references a direct child of
 * that navigation element.
 */
class StartDestinationDetector : ResourceXmlDetector() {

    companion object {
        private const val TAG_NAVIGATION = "navigation"
        private const val ATTR_START_DESTINATION = "startDestination"
        private const val APP_NAMESPACE_URI = "http://schemas.android.com/apk/res-auto"

        @JvmField
        val ISSUE: Issue = Issue.create(
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

        /**
         * Strips the leading `@id/`, `@+id/`, or similar prefix from a
         * resource reference and returns the bare name, or null if the
         * value is not a resource reference.
         */
        private fun stripIdPrefix(value: String): String? {
            // Typical forms: @id/foo  @+id/foo  @android:id/foo
            val atIndex = value.indexOf('@')
            if (atIndex < 0) return null
            val slashIndex = value.indexOf('/')
            if (slashIndex < 0) return null
            return value.substring(slashIndex + 1)
        }
    }

    // -----------------------------------------------------------------------
    // ResourceXmlDetector overrides
    // -----------------------------------------------------------------------

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.NAVIGATION
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_NAVIGATION)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // 1. Check that app:startDestination is present and non-empty.
        val startDestValue = element.getAttributeNS(APP_NAMESPACE_URI, ATTR_START_DESTINATION)

        if (startDestValue.isNullOrBlank()) {
            val location = context.getNameLocation(element)
            context.report(
                issue = ISSUE,
                element = element,
                location = location,
                message = "No start destination specified"
            )
            return
        }

        // 2. Resolve the referenced id to a bare name.
        val referencedName = stripIdPrefix(startDestValue)
        if (referencedName == null) {
            // Not a resource reference at all – report as invalid.
            val location = context.getValueLocation(
                element.getAttributeNodeNS(APP_NAMESPACE_URI, ATTR_START_DESTINATION)
            )
            context.report(
                issue = ISSUE,
                element = element,
                location = location,
                message = "Invalid start destination: `$startDestValue` is not a resource reference"
            )
            return
        }

        // 3. Verify that the referenced destination is a direct child of this
        //    <navigation> element.
        val childNodes = element.childNodes
        var found = false
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child !is Element) continue

            // The child's android:id attribute value, e.g. "@+id/foo"
            val childIdValue = child.getAttributeNS(ANDROID_URI, ATTR_ID)
            val childName = stripIdPrefix(childIdValue) ?: continue

            if (childName == referencedName) {
                found = true
                break
            }
        }

        if (!found) {
            val attrNode = element.getAttributeNodeNS(APP_NAMESPACE_URI, ATTR_START_DESTINATION)
            val location = if (attrNode != null) {
                context.getValueLocation(attrNode)
            } else {
                context.getNameLocation(element)
            }
            context.report(
                issue = ISSUE,
                element = element,
                location = location,
                message = "Start destination `$startDestValue` is not a direct child of this " +
                    "`<$TAG_NAVIGATION>` element"
            )
        }
    }
}