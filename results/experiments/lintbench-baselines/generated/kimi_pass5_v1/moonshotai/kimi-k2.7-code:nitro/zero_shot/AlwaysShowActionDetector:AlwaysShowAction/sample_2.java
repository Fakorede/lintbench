package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Prefer `showAsAction=\"ifRoom\"` over `always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                    + "Java code is usually a deviation from the user interface style guide. Use "
                    + "`ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior is "
                    + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                    + "items. Using it more than twice in the same menu is a bad idea.\n\n"
                    + "This check looks for menu XML files that contain more than two `always` "
                    + "actions, or some `always` actions and no `ifRoom` actions. In Java code, it "
                    + "looks for projects that contain references to "
                    + "`MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to "
                    + "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private int mXmlAlwaysCount;
    private int mXmlIfRoomCount;
    private Location mXmlFirstAlwaysLocation;

    private boolean mHasIfRoom;
    private final List<JavaRef> mAlwaysReferences = new ArrayList<>();

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    @NotNull
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void visitAttribute(@NotNull XmlContext context, @NotNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        boolean hasAlways = false;
        boolean hasIfRoom = false;
        for (String token : value.split("\\|")) {
            String trimmed = token.trim();
            if ("always".equals(trimmed)) {
                hasAlways = true;
            } else if ("ifRoom".equals(trimmed)) {
                hasIfRoom = true;
            }
        }

        if (hasAlways) {
            mXmlAlwaysCount++;
            if (mXmlFirstAlwaysLocation == null) {
                mXmlFirstAlwaysLocation = context.getLocation(attribute);
            }
        }
        if (hasIfRoom) {
            mXmlIfRoomCount++;
        }
    }

    @Override
    public void beforeCheckFile(@NotNull Context context) {
        if (context instanceof XmlContext) {
            mXmlAlwaysCount = 0;
            mXmlIfRoomCount = 0;
            mXmlFirstAlwaysLocation = null;
        }
    }

    @Override
    public void afterCheckFile(@NotNull Context context) {
        if (context instanceof XmlContext && mXmlFirstAlwaysLocation != null) {
            if (mXmlAlwaysCount > 2) {
                context.report(
                        ISSUE,
                        mXmlFirstAlwaysLocation,
                        "More than two `showAsAction=\"always\"` attributes in this menu; prefer `ifRoom`."
                );
            } else if (mXmlIfRoomCount == 0) {
                context.report(
                        ISSUE,
                        mXmlFirstAlwaysLocation,
                        "This menu contains `showAsAction=\"always\"` but no `ifRoom` items; prefer `ifRoom`."
                );
            }
        }
    }

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitReferenceExpression(@NotNull UReferenceExpression node) {
                PsiElement resolved = node.resolve();
                if (!(resolved instanceof PsiField)) {
                    return;
                }
                PsiField field = (PsiField) resolved;
                PsiClass containingClass = field.getContainingClass();
                if (containingClass == null
                        || !"android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                    return;
                }
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    mAlwaysReferences.add(new JavaRef(context, node));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    mHasIfRoom = true;
                }
            }
        };
    }

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mHasIfRoom = false;
        mAlwaysReferences.clear();
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        if (!mHasIfRoom && !mAlwaysReferences.isEmpty()) {
            for (JavaRef ref : mAlwaysReferences) {
                ref.context.report(
                        ISSUE,
                        ref.node,
                        ref.context.getLocation(ref.node),
                        "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` without `MenuItem.SHOW_AS_ACTION_IF_ROOM` is a deviation from the style guide."
                );
            }
        }
        mAlwaysReferences.clear();
        mHasIfRoom = false;
    }

    private static class JavaRef {
        final JavaContext context;
        final UReferenceExpression node;

        JavaRef(JavaContext context, UReferenceExpression node) {
            this.context = context;
            this.node = node;
        }
    }
}