package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Checks that classes implementing Parcelable also provide a CREATOR field.
 */
public class ParcelDetector extends Detector implements ClassScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing Parcelable `CREATOR` field",
            "According to the `Parcelable` interface documentation, \"Classes implementing " +
            "the Parcelable interface must also have a static field called `CREATOR`, which " +
            "is an object implementing the `Parcelable.Creator` interface.\"",
            Category.USABILITY,
            3,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.CLASS_FILE_SCOPE));

    private static final String PARCELABLE_OWNER = "android/os/Parcelable";
    private static final String CREATOR_FIELD_NAME = "CREATOR";

    /** Constructs a new {@link ParcelDetector} */
    public ParcelDetector() {
    }

    // ---- Implements ClassScanner ----

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(PARCELABLE_OWNER);
    }

    @Override
    public void checkClass(ClassContext context, ClassNode classNode) {
        // Skip abstract classes and interfaces - they don't need to provide CREATOR
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return;
        }
        if ((classNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        // Skip anonymous and inner classes that are not static
        // (they can't have a static CREATOR field in the traditional sense,
        // but we still check them unless they are anonymous)
        String className = classNode.name;
        if (className != null) {
            // Anonymous classes have names like "com/example/Foo$1"
            int dollarIndex = className.lastIndexOf('$');
            if (dollarIndex != -1) {
                String innerPart = className.substring(dollarIndex + 1);
                // If the part after $ is a number, it's an anonymous class
                boolean isAnonymous = true;
                for (int i = 0; i < innerPart.length(); i++) {
                    if (!Character.isDigit(innerPart.charAt(i))) {
                        isAnonymous = false;
                        break;
                    }
                }
                if (isAnonymous) {
                    return;
                }
            }
        }

        // Look for the CREATOR field
        boolean hasCreator = false;
        if (classNode.fields != null) {
            for (Object fieldObj : classNode.fields) {
                FieldNode field = (FieldNode) fieldObj;
                if (CREATOR_FIELD_NAME.equals(field.name)) {
                    // Check that it's static
                    if ((field.access & Opcodes.ACC_STATIC) != 0) {
                        hasCreator = true;
                        break;
                    }
                }
            }
        }

        if (!hasCreator) {
            String message = "This class implements `Parcelable` but does not provide a " +
                    "`CREATOR` field";
            context.report(ISSUE, context.getLocation(classNode), message);
        }
    }
}