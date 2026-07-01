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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

/**
 * Detector that finds methods that look like constructors but aren't,
 * because they have a return type declared.
 */
class WrongConstructorDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val className = declaration.name ?: return

        for (method in declaration.methods) {
            checkMethod(context, method, className)
        }
    }

    private fun checkMethod(context: JavaContext, method: UMethod, className: String) {
        // Skip actual constructors
        if (method.isConstructor) return

        val methodName = method.name

        // Check if the method name matches the class name (looks like a constructor)
        if (methodName != className) return

        // It has the same name as the class but has a return type → looks like intended constructor
        val returnType = method.returnType ?: return

        // void return type with same name as class is a strong indicator
        // Any return type (including void) with same name as class is suspicious
        val location = context.getNameLocation(method)

        val returnTypeText = returnType.presentableText

        context.report(
            issue = ISSUE,
            scope = method,
            location = location,
            message = "Method `$methodName` looks like a constructor but has return type `$returnTypeText`; " +
                "did you intend to define a constructor?"
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
                type is not a constructor — it is a regular method. This is a common mistake, \
                especially when converting code or refactoring, and the method will never be \
                called as a constructor.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}