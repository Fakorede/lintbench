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
import org.jetbrains.uast.toUElement

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This method has the same name as the class and a void return type, \
                which suggests it was intended to be a constructor.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String>? = listOf("java.lang.Object")

    override fun visitClass(context: JavaContext, declaration: UClass) {
        if (declaration.isInterface || declaration.isAnnotationType) return

        val className = declaration.name ?: return
        for (method in declaration.methods) {
            if (method.isConstructor) continue
            if (method.name != className) continue

            val returnType = method.returnType ?: continue
            val isVoid = returnType.equalsToText(PsiType.VOID)
            val isUnit = returnType.canonicalText.contains("Unit", ignoreCase = true)
            if (!isVoid && !isUnit) continue

            val uMethod = method.toUElement() ?: continue
            context.report(
                ISSUE,
                uMethod,
                context.getLocation(uMethod),
                "This method looks like a constructor but has a return type; constructors must not have a return type"
            )
        }
    }
}