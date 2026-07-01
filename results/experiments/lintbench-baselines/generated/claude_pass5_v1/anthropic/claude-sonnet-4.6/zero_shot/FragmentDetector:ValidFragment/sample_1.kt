package com.android.tools.lint.checks

import com.android.SdkConstants.CLASS_FRAGMENT
import com.android.SdkConstants.CLASS_V4_FRAGMENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.EnumSet

/**
 * Checks that Fragment subclasses can be instantiated via reflection,
 * i.e. they have a public no-argument constructor and are not inner classes.
 */
class FragmentDetector : Detector(), ClassScanner {

    companion object {
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx/fragment/app/Fragment"

        /** The main issue discovered by this detector */
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that subclasses \
                do not have other constructors with parameters, since these constructors will \
                not be called when the fragment is re-instantiated; instead, arguments can be \
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply \
                any arguments you want (as of version androidx version 1.1).
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
            implementation = Implementation(
                FragmentDetector::class.java,
                EnumSet.of(Scope.CLASS_FILE)
            )
        )

        /** Convert class name from binary (slash-separated) to internal (dot-separated) */
        private fun binaryToInternal(name: String) = name.replace('/', '.')

        /** The androidx Fragment class in binary form */
        private val ANDROIDX_FRAGMENT_BINARY = ANDROIDX_FRAGMENT_CLASS

        /** Legacy support library v4 fragment in binary form */
        private val V4_FRAGMENT_BINARY = CLASS_V4_FRAGMENT.replace('.', '/')

        /** Framework fragment in binary form */
        private val FRAMEWORK_FRAGMENT_BINARY = CLASS_FRAGMENT.replace('.', '/')
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(
            FRAMEWORK_FRAGMENT_BINARY,
            V4_FRAGMENT_BINARY,
            ANDROIDX_FRAGMENT_BINARY
        )
    }

    override fun checkClass(context: ClassContext, classNode: ClassNode) {
        // If this is an androidx Fragment subclass, skip the check because
        // FragmentFactory allows non-default constructors.
        if (isAndroidxFragment(context, classNode)) {
            return
        }

        // Abstract classes do not need to be instantiatable
        if (classNode.access and Opcodes.ACC_ABSTRACT != 0) {
            return
        }

        // Check if the class is an inner class (non-static nested class).
        // Inner classes cannot be instantiated without an enclosing instance.
        if (isInnerClass(classNode)) {
            val location = context.getLocation(classNode)
            context.report(
                ISSUE,
                location,
                "This fragment inner class should be static (${binaryToInternal(classNode.name)})"
            )
            return
        }

        // Check that the class has a public no-argument constructor.
        if (!hasPublicNoArgConstructor(classNode)) {
            val location = context.getLocation(classNode)
            context.report(
                ISSUE,
                location,
                "This fragment should provide a default constructor (a public constructor " +
                    "with no arguments) (${binaryToInternal(classNode.name)})"
            )
        }
    }

    /**
     * Determines whether this class is (or extends) an androidx Fragment,
     * which does not require a no-arg constructor.
     */
    private fun isAndroidxFragment(context: ClassContext, classNode: ClassNode): Boolean {
        // Check the direct superclass and walk up the hierarchy
        var superName: String? = classNode.superName
        while (superName != null && superName != "java/lang/Object") {
            if (superName == ANDROIDX_FRAGMENT_BINARY) {
                return true
            }
            // Try to resolve the super class
            val superClass = context.driver.findClass(context, superName, 0) ?: break
            superName = superClass.superName
        }
        return false
    }

    /**
     * Returns true if this class is a non-static inner class.
     * Non-static inner classes require an enclosing instance, so they
     * cannot be instantiated via a no-arg constructor alone.
     */
    private fun isInnerClass(classNode: ClassNode): Boolean {
        // A non-static inner class has '$' in its name and is NOT static
        val name = classNode.name
        if (!name.contains('$')) {
            return false
        }

        // Check the innerClasses list to determine if this class is a non-static inner class
        val innerClasses = classNode.innerClasses
        if (innerClasses != null) {
            for (innerClass in innerClasses) {
                val innerClassNode = innerClass as? org.objectweb.asm.tree.InnerClassNode ?: continue
                if (innerClassNode.name == name) {
                    // If the inner class is static, it's fine
                    return innerClassNode.access and Opcodes.ACC_STATIC == 0
                }
            }
        }

        // Heuristic: if the class name contains '$' and we couldn't find it in the
        // inner classes table, treat it as potentially an inner class only if it
        // doesn't appear to be a top-level class with a dollar sign in its name.
        // We'll be conservative and not report in this case.
        return false
    }

    /**
     * Returns true if the class has a public constructor with no arguments.
     */
    private fun hasPublicNoArgConstructor(classNode: ClassNode): Boolean {
        val methods = classNode.methods ?: return false
        for (method in methods) {
            val methodNode = method as? MethodNode ?: continue
            if (methodNode.name == "<init>" &&
                methodNode.desc == "()V" &&
                methodNode.access and Opcodes.ACC_PUBLIC != 0
            ) {
                return true
            }
        }
        return false
    }
}