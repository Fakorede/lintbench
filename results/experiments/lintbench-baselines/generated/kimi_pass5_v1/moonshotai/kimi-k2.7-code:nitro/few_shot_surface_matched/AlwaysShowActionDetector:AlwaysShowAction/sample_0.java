package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector
        implements SourceCodeScanner, XmlScanner {

    private static final String SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";
    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Prefer `ifRoom` over `always` for showAsAction",
                    "Using `showAsAction=\"always\"` in menu XML, or "
                            + "`MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a "
                            + "deviation from the user interface style guide. Use `ifRoom` or the "
                            + "corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
                            + "\n"
                            + "If `always` is used sparingly there are usually no problems and "
                            + "behavior is roughly equivalent to `ifRoom` but with preference "
                            + "over other `ifRoom` items. Using it more than twice in the same "
                            + "menu is a bad idea.\n"
                            + "\n"
                            + "This check looks for menu XML files that contain more than two "
                            + "`always` actions, or some `always` actions and no `ifRoom` actions. "
                            + "In Java code, it looks for projects that contain references to "
                            + "`MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to "
                            + "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private int mAlwaysCount;
    private int mIfRoomCount;
    private final List<Attr> mAlwaysAttributes = new ArrayList<>();

    private boolean mAlwaysSeen;
    private boolean mIfRoomSeen;
    private Location mAlwaysLocation;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(SHOW_AS_ACTION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
        mAlwaysAttributes.clear();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysCount > 0 && (mAlwaysCount > 2 || mIfRoomCount == 0)) {
            XmlContext xmlContext = (XmlContext) context;
            for (Attr attribute : mAlwaysAttributes) {
                xmlContext.report(
                        ISSUE,
                        attribute,
                        xmlContext.getLocation(attribute),
                        "Avoid using `showAsAction=\"always\"`; use `ifRoom` instead");
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mAlwaysSeen && !mIfRoomSeen && mAlwaysLocation != null) {
            context.report(
                    ISSUE,
                    mAlwaysLocation,
                    "Avoid using `MenuItem.SHOW_AS_ACTION_ALWAYS`; use "
                            + "`MenuItem.SHOW_AS_ACTION_IF_ROOM` instead");
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
            if (trimmed.isEmpty()) {
                continue;
            }
            if (VALUE_ALWAYS.equals(trimmed)) {
                mAlwaysCount++;
                mAlwaysAttributes.add(attribute);
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                mIfRoomCount++;
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        List<String> names = new ArrayList<>(2);
        names.add(SHOW_AS_ACTION_ALWAYS);
        names.add(SHOW_AS_ACTION_IF_ROOM);
        return names;
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @Nullable UReferenceExpression node,
            @NonNull PsiElement referenced) {
        if (!(referenced instanceof PsiField) || node == null) {
            return;
        }
        PsiField field = (PsiField) referenced;
        PsiClass containingClass = field.getContainingClass();
        if (containingClass == null
                || !MENU_ITEM_CLASS.equals(containingClass.getQualifiedName())) {
            return;
        }

        String name = field.getName();
        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            if (!mAlwaysSeen) {
                mAlwaysLocation = context.getLocation(node);
            }
            mAlwaysSeen = true;
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mIfRoomSeen = true;
        }
    }
}