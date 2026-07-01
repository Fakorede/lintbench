package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements Detector.JavaScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "in Java code is usually a deviation from the user interface style guide. "
                            + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                            + "If `always` is used sparingly there are usually no problems and behavior is "
                            + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. "
                            + "Using it more than twice in the same menu is a bad idea.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int mAlwaysCount;
    private int mIfRoomCount;
    private Attr mFirstAlways;

    private boolean mSeenIfRoom;
    private final List<ReferenceInfo> mAlwaysReferences = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
        mFirstAlways = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysCount > 0 && (mAlwaysCount > 2 || mIfRoomCount == 0)) {
            XmlContext xmlContext = (XmlContext) context;
            String message;
            if (mAlwaysCount > 2) {
                message =
                        "This menu contains more than two `showAsAction=\"always\"` items; "
                                + "use `showAsAction=\"ifRoom\"` instead";
            } else {
                message =
                        "This menu contains `showAsAction=\"always\"` but no `showAsAction=\"ifRoom\"`; "
                                + "use `ifRoom` instead";
            }
            xmlContext.report(ISSUE, xmlContext.getLocation(mFirstAlways), message);
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        for (String flag : value.split("\\|")) {
            String trimmed = flag.trim();
            if ("always".equals(trimmed)) {
                mAlwaysCount++;
                if (mFirstAlways == null) {
                    mFirstAlways = attribute;
                }
            } else if ("ifRoom".equals(trimmed)) {
                mIfRoomCount++;
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(
                "SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement referenced) {
        String name = reference.getIdentifier();
        if (name == null) {
            return;
        }

        if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            if (isMenuItemReference(referenced)) {
                mSeenIfRoom = true;
            }
        } else if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            if (isMenuItemReference(referenced)) {
                mAlwaysReferences.add(new ReferenceInfo(context, reference));
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mSeenIfRoom && !mAlwaysReferences.isEmpty()) {
            String message =
                    "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` without `MenuItem.SHOW_AS_ACTION_IF_ROOM` "
                            + "is discouraged; use `SHOW_AS_ACTION_IF_ROOM` instead";
            for (ReferenceInfo info : mAlwaysReferences) {
                info.context.report(
                        ISSUE, info.context.getLocation(info.reference), message);
            }
        }
    }

    private static boolean isMenuItemReference(@NonNull PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiClass containingClass = ((PsiField) referenced).getContainingClass();
            return containingClass != null && "MenuItem".equals(containingClass.getName());
        }
        return false;
    }

    private static class ReferenceInfo {
        final JavaContext context;
        final UReferenceExpression reference;

        ReferenceInfo(JavaContext context, UReferenceExpression reference) {
            this.context = context;
            this.reference = reference;
        }
    }
}