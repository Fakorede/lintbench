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
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass

/**
 * Detector that finds methods that look like constructors (same name as the enclosing class)
 * but have a return type, meaning they are actually regular methods, not constructors.
 */
class WrongConstructorDetector : Detector(), Detector.UastScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be constructors, \
                but aren't.

                In Java, a constructor looks like a method with the same name as the enclosing \
                class, but without a return type. If you accidentally include a return type \
                (even `void`), the method becomes a regular method instead of a constructor, \
                and the class will not be properly initialized when instantiated.
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

    override fun createUastHandler(context: JavaContext): org.jetbrains.uast.visitor.AbstractUastVisitor {
        return object : org.jetbrains.uast.visitor.AbstractUastVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                checkMethod(context, node)
                return false
            }
        }
    }

    private fun checkMethod(context: JavaContext, node: UMethod) {
        // If it's already a constructor, skip it
        if (node.isConstructor) {
            return
        }

        val containingClass: UClass = node.getContainingUClass() ?: return

        val methodName = node.name
        val className = containingClass.name ?: return

        // Check if the method name matches the class name (looks like a constructor)
        if (methodName != className) {
            return
        }

        // This method has the same name as the class but has a return type,
        // so it's not actually a constructor.
        context.report(
            issue = ISSUE,
            scope = node as UElement,
            location = context.getLocation(node as UElement),
            message = "Method `$methodName` looks like a constructor but has a return type; " +
                "did you mean to declare a constructor?"
        )
    }
}