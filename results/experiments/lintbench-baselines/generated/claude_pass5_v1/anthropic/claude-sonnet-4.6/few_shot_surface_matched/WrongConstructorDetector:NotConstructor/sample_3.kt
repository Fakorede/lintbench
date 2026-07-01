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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.visitor.AbstractUastVisitor

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be \
                constructors, but aren't.

                If you have a method that has the same name as the containing class, \
                then this looks like an attempt to write a constructor, but it will \
                actually be a regular method. If this was intentional, consider \
                renaming the method to something less confusing. If not, add the \
                missing return type declaration to suppress this warning (or rename \
                the method).
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

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): AbstractUastVisitor {
        return object : AbstractUastVisitor() {
            override fun visitClass(node: UClass): Boolean {
                val className = node.name ?: return false
                for (method in node.methods) {
                    visitMethod(context, method, className)
                }
                return false
            }
        }
    }

    private fun visitMethod(context: JavaContext, method: UMethod, className: String) {
        // Skip actual constructors
        if (method.isConstructor) {
            return
        }

        // Check if the method name matches the class name
        if (method.name != className) {
            return
        }

        // If the return type is void or matches the class, it looks like an intended constructor
        val returnType: PsiType = method.returnType ?: return

        val message = "Method ${method.name} looks like a constructor but has a return type; " +
            "is this intended to be a constructor?"

        context.report(
            ISSUE,
            method,
            context.getNameLocation(method),
            message
        )
    }
}