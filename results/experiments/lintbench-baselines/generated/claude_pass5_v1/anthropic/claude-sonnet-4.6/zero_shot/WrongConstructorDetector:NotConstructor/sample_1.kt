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
import com.intellij.psi.PsiType
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

/**
 * Detector that finds methods that look like they were intended to be
 * constructors but are not (e.g. they have a return type, making them
 * regular methods instead of constructors).
 */
class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String>? = null

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

        // Check if the method name matches the class name (case-sensitive)
        if (methodName != className) return

        // It has the same name as the class but is NOT a constructor.
        // This typically happens when a return type is accidentally specified.
        val psiMethod = method.javaPsi as? PsiMethod ?: return

        // Verify it has a return type (which is what makes it a method rather than a constructor)
        val returnType: PsiType? = psiMethod.returnType
        // If returnType is null it IS a constructor at the PSI level; skip it.
        if (returnType == null) return

        val returnTypeName = returnType.presentableText

        context.report(
            issue = ISSUE,
            scope = method,
            location = context.getNameLocation(method),
            message = "Method `$methodName` looks like a constructor but has return type " +
                "`$returnTypeName`; did you intend this to be a constructor?"
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

                A method has the same name as its enclosing class, which is the hallmark of a \
                constructor in Java. However, because it specifies a return type, it is treated \
                as a regular method rather than a constructor.

                This is almost always a mistake: the developer forgot to remove the return type \
                (or added one accidentally), causing the method to never be called as a \
                constructor.
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