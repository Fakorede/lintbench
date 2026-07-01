/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_SCALE_TYPE
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Checks whether a LinearLayout with an ImageView and a TextView can be
 * replaced by a TextView with compound drawables.
 */
class UseCompoundDrawableDetector : LayoutDetector() {

    companion object {
        /** The main issue discovered by this detector */
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "UseCompoundDrawables",
            briefDescription = "Node can be replaced by a `TextView` with compound drawables",
            explanation = """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes \
                to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be replaced \
                with a `drawablePadding` attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
                """,
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                UseCompoundDrawableDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(LINEAR_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        // Collect the child elements (ignore whitespace text nodes)
        val children = getChildren(element)

        // We're looking for exactly 2 children: one ImageView and one TextView
        if (children.size != 2) {
            return
        }

        val first = children[0]
        val second = children[1]

        val hasImageAndText =
            (isImageView(first) && isTextView(second)) ||
                (isTextView(first) && isImageView(second))

        if (!hasImageAndText) {
            return
        }

        // If the LinearLayout has a background, we can't easily convert it
        if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
            return
        }

        // Find the ImageView among the children
        val imageView = if (isImageView(first)) first else second

        // If the ImageView has a background, skip (compound drawables don't support backgrounds)
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
            return
        }

        // If the ImageView has a non-standard scaleType, skip
        if (imageView.hasAttributeNS(ANDROID_URI, ATTR_SCALE_TYPE)) {
            return
        }

        context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "This tag and its children can be replaced by one `<TextView/>` and " +
                "a compound drawable"
        )
    }

    /**
     * Returns the child [Element] nodes of the given element,
     * ignoring whitespace text nodes and other non-element nodes.
     */
    private fun getChildren(element: Element): List<Element> {
        val children = mutableListOf<Element>()
        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                children.add(child as Element)
            }
            child = child.nextSibling
        }
        return children
    }

    private fun isImageView(element: Element): Boolean {
        return element.tagName == IMAGE_VIEW
    }

    private fun isTextView(element: Element): Boolean {
        return element.tagName == TEXT_VIEW
    }
}