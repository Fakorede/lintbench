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

import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLL_VIEW
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Checks that ScrollView children use wrap_content rather than fill_parent/match_parent
 * in the scrolling dimension.
 */
class ScrollViewChildDetector : LayoutDetector() {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ScrollViewSize",
            briefDescription = "ScrollView size validation",
            explanation = """
                ScrollView children must set their `layout_width` or `layout_height` \
                attributes to `wrap_content` rather than `fill_parent` or `match_parent` \
                in the scrolling dimension.
                """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScrollViewChildDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val isHorizontal = element.tagName == HORIZONTAL_SCROLL_VIEW

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node is Element) {
                val child = node
                if (isHorizontal) {
                    // HorizontalScrollView scrolls horizontally, so layout_width must be wrap_content
                    val widthAttr = child.getAttributeNS(
                        "http://schemas.android.com/apk/res/android",
                        ATTR_LAYOUT_WIDTH
                    )
                    if (widthAttr == VALUE_FILL_PARENT || widthAttr == VALUE_MATCH_PARENT) {
                        val attrNode = child.getAttributeNodeNS(
                            "http://schemas.android.com/apk/res/android",
                            ATTR_LAYOUT_WIDTH
                        )
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(attrNode ?: child),
                            "This child view should use `wrap_content` for its `layout_width` " +
                                "rather than `$widthAttr` since the parent is a `HorizontalScrollView`"
                        )
                    }
                } else {
                    // ScrollView scrolls vertically, so layout_height must be wrap_content
                    val heightAttr = child.getAttributeNS(
                        "http://schemas.android.com/apk/res/android",
                        ATTR_LAYOUT_HEIGHT
                    )
                    if (heightAttr == VALUE_FILL_PARENT || heightAttr == VALUE_MATCH_PARENT) {
                        val attrNode = child.getAttributeNodeNS(
                            "http://schemas.android.com/apk/res/android",
                            ATTR_LAYOUT_HEIGHT
                        )
                        context.report(
                            ISSUE,
                            child,
                            context.getLocation(attrNode ?: child),
                            "This child view should use `wrap_content` for its `layout_height` " +
                                "rather than `$heightAttr` since the parent is a `ScrollView`"
                        )
                    }
                }
            }
        }
    }
}