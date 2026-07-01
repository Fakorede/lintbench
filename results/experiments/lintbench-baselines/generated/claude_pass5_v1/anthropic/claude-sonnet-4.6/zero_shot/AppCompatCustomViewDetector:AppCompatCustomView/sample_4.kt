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
import com.intellij.psi.PsiClass
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

        // The set of appcompat replacements (we should not flag classes that already extend these)
        private val APPCOMPAT_WIDGET_CLASSES: Set<String> = APPCOMPAT_SUPER_MAP.values.toSet()
    }

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                // Check if this class extends one of the android.widget classes directly
                val superClass = node.javaPsi.superClass ?: return
                val superClassName = superClass.qualifiedName ?: return

                // Check if the super class is one we should flag
                val appCompatReplacement = APPCOMPAT_SUPER_MAP[superClassName] ?: return

                // Don't flag if the class is itself an appcompat widget
                val qualifiedName = node.qualifiedName
                if (qualifiedName != null && APPCOMPAT_WIDGET_CLASSES.contains(qualifiedName)) {
                    return
                }

                // Check if appcompat is on the classpath; if not, skip the warning
                if (!hasAppCompatDependency(context)) {
                    return
                }

                // Find the extends clause location for better error reporting
                val location = context.getNameLocation(node)

                context.report(
                    ISSUE,
                    node,
                    location,
                    "This custom view should extend `$appCompatReplacement` instead"
                )
            }
        }
    }

    private fun hasAppCompatDependency(context: JavaContext): Boolean {
        // Check if any of the appcompat widget classes are available on the classpath
        val evaluator = context.evaluator
        for (appCompatClass in APPCOMPAT_WIDGET_CLASSES) {
            val psiClass: PsiClass? = evaluator.findClass(appCompatClass)
            if (psiClass != null) {
                return true
            }
        }
        return false
    }
}