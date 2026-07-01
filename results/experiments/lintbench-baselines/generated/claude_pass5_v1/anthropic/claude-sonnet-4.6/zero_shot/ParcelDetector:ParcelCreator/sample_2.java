package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.ClassScanner;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

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
            "According to the `Parcelable` interface documentation, " +
            "\"Classes implementing the Parcelable interface must also have a static " +
            "field called `CREATOR`, which is an object implementing the " +
            "`Parcelable.Creator` interface.\"",
            Category.USABILITY,
            3,
            Severity.ERROR,
            new Implementation(
                    ParcelDetector.class,
                    Scope.CLASS_FILE_SCOPE));

    private static final String PARCELABLE_OWNER = "android/os/Parcelable";
    private static final String CREATOR_FIELD = "CREATOR";

    /** Constructs a new {@link ParcelDetector} */
    public ParcelDetector() {
    }

    // ---- Implements ClassScanner ----

    @Nullable
    @Override
    public List<String> getApplicableCallNames() {
        return null;
    }

    @Override
    public void checkClass(@NonNull ClassContext context, @NonNull ClassNode classNode) {
        // Check if this class is abstract or an interface - if so, skip it
        if ((classNode.access & org.objectweb.asm.Opcodes.ACC_ABSTRACT) != 0 ||
                (classNode.access & org.objectweb.asm.Opcodes.ACC_INTERFACE) != 0) {
            return;
        }

        // Check if this class implements Parcelable
        if (!implementsParcelable(classNode)) {
            return;
        }

        // Check if this class has a CREATOR field
        if (!hasCreatorField(classNode)) {
            context.report(
                    ISSUE,
                    context.getLocation(classNode),
                    "This class implements `Parcelable` but does not provide a " +
                    "`CREATOR` field");
        }
    }

    /**
     * Checks whether the given class node implements the Parcelable interface.
     */
    private static boolean implementsParcelable(@NonNull ClassNode classNode) {
        List interfaces = classNode.interfaces;
        if (interfaces != null) {
            for (Object iface : interfaces) {
                if (PARCELABLE_OWNER.equals(iface)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks whether the given class node has a static CREATOR field.
     */
    private static boolean hasCreatorField(@NonNull ClassNode classNode) {
        List fields = classNode.fields;
        if (fields != null) {
            for (Object f : fields) {
                FieldNode field = (FieldNode) f;
                if (CREATOR_FIELD.equals(field.name)) {
                    // Check that it's static
                    if ((field.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}