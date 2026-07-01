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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
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
            new Implementation(AlwaysShowActionDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                            + "Java code is usually a deviation from the user interface style guide. "
                            + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                            + "If `always` is used sparingly there are usually no problems and behavior is "
                            + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                            + "items. Using it more than twice in the same menu is a bad idea.\n\n"
                            + "This check looks for menu XML files that contain more than two `always` "
                            + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                            + "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private final List<Attr> mXmlAlways = new ArrayList<>();
    private boolean mXmlHasIfRoom = false;

    private final List<JavaReference> mJavaAlways = new ArrayList<>();
    private boolean mJavaHasIfRoom = false;

    private static class JavaReference {
        final JavaContext context;
        final UReferenceExpression reference;

        JavaReference(JavaContext context, UReferenceExpression reference) {
            this.context = context;
            this.reference = reference;
        }
    }

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
        if (context instanceof XmlContext) {
            mXmlAlways.clear();
            mXmlHasIfRoom = false;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            int alwaysCount = mXmlAlways.size();
            if (alwaysCount > 2) {
                for (Attr attr : mXmlAlways) {
                    xmlContext.report(
                            ISSUE,
                            attr,
                            xmlContext.getLocation(attr),
                            "Prefer \"`ifRoom`\" instead of \"`always`\" to avoid crowding the action bar. "
                                    + "Also, do not use \"`always`\" more than twice in the same menu (found "
                                    + alwaysCount + ").");
                }
            } else if (alwaysCount > 0 && !mXmlHasIfRoom) {
                for (Attr attr : mXmlAlways) {
                    xmlContext.report(
                            ISSUE,
                            attr,
                            xmlContext.getLocation(attr),
                            "Prefer \"`ifRoom`\" instead of \"`always`\" to avoid crowding the action bar.");
                }
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mJavaAlways.isEmpty() && !mJavaHasIfRoom) {
            for (JavaReference ref : mJavaAlways) {
                ref.context.report(
                        ISSUE,
                        ref.reference,
                        ref.context.getLocation(ref.reference),
                        "Prefer \"`SHOW_AS_ACTION_IF_ROOM`\" instead of \"`SHOW_AS_ACTION_ALWAYS`\" to avoid crowding the action bar.");
            }
        }
        mJavaAlways.clear();
        mJavaHasIfRoom = false;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null) {
            String[] parts = value.split("\\|");
            for (String part : parts) {
                String trimmed = part.trim();
                if ("always".equals(trimmed)) {
                    mXmlAlways.add(attribute);
                } else if ("ifRoom".equals(trimmed)) {
                    mXmlHasIfRoom = true;
                }
            }
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
        if (isMenuItemField(referenced)) {
            String name = reference.getResolvedName();
            if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                mJavaAlways.add(new JavaReference(context, reference));
            } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                mJavaHasIfRoom = true;
            }
        }
    }

    private boolean isMenuItemField(PsiElement resolved) {
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null) {
                String qName = containingClass.getQualifiedName();
                return "android.view.MenuItem".equals(qName)
                        || "androidx.core.view.MenuItemCompat".equals(qName)
                        || "android.support.v4.view.MenuItemCompat".equals(qName);
            }
        }
        return false;
    }
}