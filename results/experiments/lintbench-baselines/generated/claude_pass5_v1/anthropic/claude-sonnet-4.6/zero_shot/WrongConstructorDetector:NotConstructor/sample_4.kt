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
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

/**
 * Detector that finds methods that look like they were intended to be
 * constructors but aren't (e.g., they have a return type).
 */
class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? = null

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val className = declaration.name ?: return

        for (method in declaration.methods) {
            checkMethod(context, declaration, method, className)
        }
    }

    private fun checkMethod(
        context: JavaContext,
        containingClass: UClass,
        method: UMethod,
        className: String
    ) {
        // Skip actual constructors
        if (method.isConstructor) return

        val methodName = method.name

        // Check if the method name matches the class name
        if (methodName != className) return

        // This method has the same name as the class but has a return type,
        // meaning it looks like a constructor but is actually a regular method.
        val returnTypeElement = method.returnTypeElement ?: return

        // In Java, a method with the same name as the class but with a return type
        // is not a constructor. This is likely a mistake.
        val location = context.getNameLocation(method)

        context.report(
            issue = ISSUE,
            scope = method,
            location = location,
            message = "Method `$methodName` looks like a constructor but has a return type; " +
                "did you intend for this to be a constructor? " +
                "(Remove the return type to make it a constructor)"
        )
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be \
                constructors, but aren't.

                In Java, a constructor looks like a method but has no return type. \
                If a method has the same name as the enclosing class but also has a \
                return type, it is a regular method, not a constructor. This is \
                sometimes done by accident, particularly when code is converted or \
                refactored.
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