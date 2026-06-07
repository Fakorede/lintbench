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
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

/**
 * Detector that finds methods that look like they were intended to be
 * constructors but aren't (e.g., they have a return type declared).
 */
class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be \
                constructors, but aren't because they have a return type declared. \
                In Java, a constructor must not have a return type. If a return type \
                is specified (even `void`), the method becomes a regular method rather \
                than a constructor, which is almost certainly not what was intended.
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

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun visitMethod(context: JavaContext, node: UMethod) {
        // Skip actual constructors
        if (node.isConstructor) return

        val psiMethod: PsiMethod = node.javaPsi

        // Get the method name
        val methodName = psiMethod.name

        // Find the containing class
        val containingClass = psiMethod.containingClass ?: return
        val className = containingClass.name ?: return

        // Check if the method name matches the class name
        if (methodName != className) return

        // The method has the same name as the class but has a return type,
        // making it a regular method instead of a constructor.
        val returnType: PsiType = psiMethod.returnType ?: return

        val returnTypeText = returnType.presentableText

        context.report(
            issue = ISSUE,
            scope = node,
            location = context.getNameLocation(node),
            message = "Method `$methodName` looks like a constructor but has return type " +
                "`$returnTypeText`; did you intend it to be a constructor? " +
                "Remove the return type to make it a constructor."
        )
    }
}