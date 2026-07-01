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

        // Map from android.widget class to the recommended AppCompat replacement (androidx)
        private val ANDROIDX_SUPER_MAP: Map<String, String> = mapOf(
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

        // Map from android.widget class to the recommended AppCompat replacement (old support lib)
        private val SUPPORT_SUPER_MAP: Map<String, String> = mapOf(
            "android.widget.AutoCompleteTextView" to "android.support.v7.widget.AppCompatAutoCompleteTextView",
            "android.widget.Button" to "android.support.v7.widget.AppCompatButton",
            "android.widget.CheckBox" to "android.support.v7.widget.AppCompatCheckBox",
            "android.widget.CheckedTextView" to "android.support.v7.widget.AppCompatCheckedTextView",
            "android.widget.EditText" to "android.support.v7.widget.AppCompatEditText",
            "android.widget.ImageButton" to "android.support.v7.widget.AppCompatImageButton",
            "android.widget.ImageView" to "android.support.v7.widget.AppCompatImageView",
            "android.widget.MultiAutoCompleteTextView" to "android.support.v7.widget.AppCompatMultiAutoCompleteTextView",
            "android.widget.RadioButton" to "android.support.v7.widget.AppCompatRadioButton",
            "android.widget.RatingBar" to "android.support.v7.widget.AppCompatRatingBar",
            "android.widget.SeekBar" to "android.support.v7.widget.AppCompatSeekBar",
            "android.widget.Spinner" to "android.support.v7.widget.AppCompatSpinner",
            "android.widget.TextView" to "android.support.v7.widget.AppCompatTextView",
            "android.widget.ToggleButton" to "android.support.v7.widget.AppCompatToggleButton"
        )

        // All acceptable AppCompat super classes (extending these is fine)
        private val APPCOMPAT_WIDGET_CLASSES: Set<String> =
            ANDROIDX_SUPER_MAP.values.toSet() + SUPPORT_SUPER_MAP.values.toSet()
    }

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                checkClass(context, node)
            }
        }
    }

    private fun checkClass(context: JavaContext, node: UClass) {
        val evaluator = context.evaluator

        // Walk the super class hierarchy to find if any ancestor is an android.widget class
        // that has an AppCompat replacement, but make sure the immediate super isn't already
        // an AppCompat class.
        val superClass = node.javaPsi.superClass ?: return
        val superClassName = superClass.qualifiedName ?: return

        // If the super class is already an AppCompat widget, no warning needed
        if (APPCOMPAT_WIDGET_CLASSES.contains(superClassName)) {
            return
        }

        // Check if the direct super class is one of the android.widget classes we care about
        val androidxReplacement = ANDROIDX_SUPER_MAP[superClassName]
        val supportReplacement = SUPPORT_SUPER_MAP[superClassName]

        if (androidxReplacement == null && supportReplacement == null) {
            return
        }

        // Determine which appcompat variant is available in the project
        val hasAndroidX = evaluator.findClass("androidx.appcompat.widget.AppCompatTextView") != null
        val hasSupport = evaluator.findClass("android.support.v7.widget.AppCompatTextView") != null

        if (!hasAndroidX && !hasSupport) {
            return
        }

        val replacement = when {
            hasAndroidX && androidxReplacement != null -> androidxReplacement
            hasSupport && supportReplacement != null -> supportReplacement
            hasAndroidX -> androidxReplacement ?: return
            else -> supportReplacement ?: return
        }

        // Find the extends reference in the source
        val superClassReference = node.uastSuperTypes.firstOrNull { typeRef ->
            val resolvedName = typeRef.getQualifiedName()
            resolvedName == superClassName
        }

        val location = if (superClassReference != null) {
            context.getLocation(superClassReference)
        } else {
            context.getNameLocation(node)
        }

        context.report(
            ISSUE,
            node,
            location,
            "This custom view should extend `$replacement` instead"
        )
    }
}