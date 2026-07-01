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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

/**
 * Detector that finds methods that look like constructors (same name as the enclosing class)
 * but have a return type, meaning they are actually regular methods, not constructors.
 */
class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? = null

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun visitMethod(context: JavaContext, node: UMethod, method: PsiMethod) {
        // We only care about methods that have a return type (i.e., are NOT constructors)
        // but whose name matches the enclosing class name.
        if (node.isConstructor) {
            // It really is a constructor, nothing to report
            return
        }

        val methodName = node.name

        // Find the containing class
        val containingClass: PsiClass = method.containingClass ?: return

        val className = containingClass.name ?: return

        // Check if the method name matches the class name
        if (methodName != className) {
            return
        }

        // This method has the same name as its enclosing class but has a return type,
        // so it looks like an intended constructor but isn't one.
        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getNameLocation(node),
            message = "Method `$methodName` looks like a constructor but has a return type; " +
                "did you intend it to be a constructor?"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be constructors, \
                but aren't.

                A method that has the same name as its enclosing class but also declares a return \
                type is a regular method, not a constructor. This is often a mistake where the \
                developer forgot to omit the return type when writing a constructor.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}