package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AlwaysShowActionDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS`"
                            + " in Java code is usually a deviation from the user interface style"
                            + " guide. Use `ifRoom` or the corresponding"
                            + " `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead. If `always` is used"
                            + " sparingly there are usually no problems and behavior is roughly"
                            + " equivalent to `ifRoom` but with preference over other `ifRoom`"
                            + " items. Using it more than twice in the same menu is a bad idea.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mAlwaysCount;
    private int mIfRoomCount;
    private final List<Attr> mAlwaysAttributes = new ArrayList<>();

    private final List<JavaReference> mJavaAlwaysReferences = new ArrayList<>();
    private boolean mHasJavaIfRoom;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        if (context.getResourceFolderType() == ResourceFolderType.MENU) {
            mAlwaysCount = 0;
            mIfRoomCount = 0;
            mAlwaysAttributes.clear();
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }
        if (mAlwaysCount > 2 || (mAlwaysCount > 0 && mIfRoomCount == 0)) {
            Attr attribute = mAlwaysAttributes.get(0);
            context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Avoid using `showAsAction=\"always\"`; use `ifRoom` instead");
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }
        if (containsFlag(value, "always")) {
            mAlwaysCount++;
            mAlwaysAttributes.add(attribute);
        }
        if (containsFlag(value, "ifRoom")) {
            mIfRoomCount++;
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement referenced) {
        if (!(referenced instanceof PsiNamedElement)) {
            return;
        }
        String name = ((PsiNamedElement) referenced).getName();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mJavaAlwaysReferences.add(new JavaReference(context, reference));
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mHasJavaIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mJavaAlwaysReferences.isEmpty() && !mHasJavaIfRoom) {
            for (JavaReference ref : mJavaAlwaysReferences) {
                JavaContext javaContext = ref.context;
                UReferenceExpression reference = ref.reference;
                javaContext.report(
                        ISSUE,
                        reference,
                        javaContext.getLocation(reference),
                        "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` over"
                                + " `MenuItem.SHOW_AS_ACTION_ALWAYS`");
            }
        }
        mJavaAlwaysReferences.clear();
        mHasJavaIfRoom = false;
    }

    private static boolean containsFlag(@NonNull String value, @NonNull String flag) {
        for (String part : value.split("\\|")) {
            if (flag.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private static class JavaReference {
        final JavaContext context;
        final UReferenceExpression reference;

        JavaReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference) {
            this.context = context;
            this.reference = reference;
        }
    }
}