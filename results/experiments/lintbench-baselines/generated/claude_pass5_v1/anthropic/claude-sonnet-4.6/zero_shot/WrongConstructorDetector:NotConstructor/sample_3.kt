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
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

/**
 * Detector that finds methods that look like constructors (same name as the enclosing class)
 * but have a return type, meaning they are regular methods, not constructors.
 */
class WrongConstructorDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun visitMethod(context: JavaContext, node: UMethod) {
        // We are looking for methods (not constructors) whose name matches the enclosing class name
        if (node.isConstructor) {
            return
        }

        val methodName = node.name

        // Find the containing class
        val containingClass: UClass = node.getContainingUClass() ?: return

        val className = containingClass.name ?: return

        // Check if the method name matches the class name
        if (methodName != className) {
            return
        }

        // This method has the same name as the class but has a return type,
        // so it's not a constructor — it looks like a mistaken constructor declaration.
        val returnType = node.returnType
        val returnTypeText = returnType?.canonicalText ?: "void"

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getNameLocation(node),
            message = "Method `$methodName` looks like a constructor but has return type `$returnTypeText`; " +
                "did you intend this to be a constructor? (Remove the return type to make it a constructor)"
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
                type is a regular method, not a constructor. This is a common mistake, especially \
                when migrating code or when the return type is accidentally added.

                To fix this, remove the return type from the method declaration if you intended \
                it to be a constructor.
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