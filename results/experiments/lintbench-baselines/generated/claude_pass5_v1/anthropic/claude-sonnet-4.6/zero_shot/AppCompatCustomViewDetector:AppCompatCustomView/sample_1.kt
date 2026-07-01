/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass

class AppCompatCustomViewDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            AppCompatCustomViewDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "AppCompatCustomView",
            briefDescription = "Appcompat Custom Widgets",
            explanation = """
                In order to support features such as tinting, the appcompat library will \
                automatically load special appcompat replacements for the builtin widgets. \
                However, this does not work for your own custom views.

                Instead of extending the `android.widget` classes directly, you should \
                instead extend one of the delegate classes in \
                `androidx.appcompat.widget.AppCompatTextView`.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION
        )

        // Map from android.widget class to its appcompat replacement
        private val APPCOMPAT_SUPER_MAP: Map<String, String> = mapOf(
            "android.widget.AutoCompleteTextView" to "androidx.appcompat.widget.AppCompatAutoCompleteTextView",
            "android.widget.Button" to "androidx.appcompat.widget.AppCompatButton",
            "android.widget.CheckBox" to "androidx.appcompat.widget.AppCompatCheckBox",
            "android.widget.CheckedTextView" to "androidx.appcompat.widget.AppCompatCheckedTextView",
            "android.widget.EditText" to "androidx.appcompat.widget.AppCompatEditText",
            "android.widget.ImageButton" to "androidx.appcompat.widget.AppCompatImageButton",
            "android.widget.ImageView" to "androidx.appcompat.widget.AppCompatImageView",
            "android.widget.MultiAutoCompleteTextView" to "androidx.appcompat.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.RadioButton" to "androidx.appcompat.widget.AppCompatRadioButton",
            "android.widget.RatingBar" to "androidx.appcompat.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "androidx.appcompat.widget.AppCompatSeekBar",
            "android.widget.Spinner" to "androidx.appcompat.widget.AppCompatSpinner",
            "android.widget.TextView" to "androidx.appcompat.widget.AppCompatTextView",
            "android.widget.ToggleButton" to "androidx.appcompat.widget.AppCompatToggleButton"
        )

        // Set of appcompat widget classes (we don't want to warn if someone extends these)
        private val APPCOMPAT_WIDGET_CLASSES: Set<String> = APPCOMPAT_SUPER_MAP.values.toSet()
    }

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                // Get the super class
                val superClass = node.javaPsi.superClass ?: return
                val superClassName = superClass.qualifiedName ?: return

                // Check if the super class is one of the android.widget classes we care about
                val replacement = APPCOMPAT_SUPER_MAP[superClassName] ?: return

                // Make sure we're not in the appcompat library itself
                val currentClassName = node.qualifiedName ?: return
                if (currentClassName.startsWith("androidx.appcompat.") ||
                    currentClassName.startsWith("android.support.v7.")) {
                    return
                }

                // Check that the appcompat library is actually on the classpath
                // (no point warning if they're not using appcompat)
                val evaluator = context.evaluator
                if (evaluator.findClass("androidx.appcompat.widget.AppCompatTextView") == null &&
                    evaluator.findClass("android.support.v7.widget.AppCompatTextView") == null) {
                    return
                }

                // Find the extends list element to report the issue on
                val extendsListElement = node.uastSuperTypes.firstOrNull {
                    val resolvedClass = it.getType().let { type ->
                        evaluator.getTypeClass(type)
                    }
                    resolvedClass?.qualifiedName == superClassName
                }

                val locationElement = extendsListElement ?: node

                context.report(
                    ISSUE,
                    locationElement,
                    context.getLocation(locationElement),
                    "This custom view should extend `$replacement` instead"
                )
            }
        }
    }
}