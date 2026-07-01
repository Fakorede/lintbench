package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be constructors, \
                but aren't.

                A method that has the same name as the enclosing class but has a return type is \
                not a constructor. This is a common mistake, especially when converting from one \
                language to another or when refactoring code.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf("java.lang.Object")
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        val className = declaration.name ?: return

        for (method in declaration.methods) {
            // Skip actual constructors
            if (method.isConstructor) continue

            val methodName = method.name

            // Check if the method name matches the class name
            if (methodName == className) {
                // This method looks like a constructor but has a return type
                context.report(
                    ISSUE,
                    method,
                    context.getNameLocation(method),
                    "Method `$methodName` looks like a constructor but has a return type; " +
                            "did you mean to be a constructor?"
                )
            }
        }
    }
}