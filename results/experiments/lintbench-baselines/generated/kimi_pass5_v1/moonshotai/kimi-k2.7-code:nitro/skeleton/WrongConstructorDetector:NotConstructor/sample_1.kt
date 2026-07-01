package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WrongConstructorDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = "This method has the same name as its containing class and declares a return type, so it is not a constructor. " +
                    "A constructor must not specify a return type. As a result, this method will be treated as an ordinary method rather than a constructor, which is usually a mistake.",
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) {
                    return
                }

                val methodName = node.name ?: return
                val containingClass = node.getParentOfType(UClass::class.java, true) ?: return
                val className = containingClass.name ?: return

                if (methodName != className) {
                    return
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "This method looks like a constructor but has a return type, so it is not a constructor",
                )
            }
        }
}