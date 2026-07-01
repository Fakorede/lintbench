package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun applicableSuperClasses(): List<String> =
        listOf("java.lang.Object", "kotlin.Any")

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface || declaration.isAnnotationType) return

        val className = declaration.name ?: return
        for (method in declaration.methods) {
            if (method.isConstructor) continue
            if (method.hasModifierProperty(PsiModifier.STATIC)) continue
            if (method.containingClass?.isInterface == true) continue

            val methodName = method.name ?: continue
            if (methodName != className) continue

            val returnType = method.returnType
            val message = if (returnType != null) {
                "Method looks like a constructor but has return type '$returnType'; " +
                        "remove the return type if a constructor was intended."
            } else {
                "Method looks like a constructor but is not a constructor."
            }

            context.report(
                ISSUE,
                method,
                context.getLocation(method),
                message
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This method has the same name as its containing class and declares a return type, \
                so it is treated as an ordinary method rather than a constructor. If it was meant \
                to be a constructor, remove the return type.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}