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
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner {

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or "
                            + "`MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a "
                            + "deviation from the user interface style guide. Use `ifRoom` or "
                            + "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
                            + "\n"
                            + "If `always` is used sparingly there are usually no problems and "
                            + "behavior is roughly equivalent to `ifRoom` but with preference "
                            + "over other `ifRoom` items. Using it more than twice in the same "
                            + "menu is a bad idea.\n"
                            + "\n"
                            + "This check looks for menu XML files that contain more than two "
                            + "`always` actions, or some `always` actions and no `ifRoom` "
                            + "actions. In Java code, it looks for projects that contain "
                            + "references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references "
                            + "to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    6,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE),
                            Scope.RESOURCE_FILE_SCOPE,
                            Scope.JAVA_FILE_SCOPE));

    // Per-file state for XML scanning
    private int mAlwaysCount;
    private boolean mHasIfRoom;
    private Location mFirstAlwaysLocation;

    // Project-level state for Java scanning
    private int mJavaAlwaysCount;
    private boolean mJavaHasIfRoom;
    private Location mFirstJavaAlwaysLocation;

    public AlwaysShowActionDetector() {}

    // ---- ResourceXmlDetector ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTR_SHOW_AS_ACTION,
                "android:showAsAction");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysCount = 0;
        mHasIfRoom = false;
        mFirstAlwaysLocation = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        if (value.contains(VALUE_ALWAYS)) {
            mAlwaysCount++;
            if (mFirstAlwaysLocation == null) {
                mFirstAlwaysLocation = context.getLocation(attribute);
            }
        }
        if (value.contains(VALUE_IF_ROOM)) {
            mHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysCount == 0) {
            return;
        }
        if (mAlwaysCount > 2) {
            Location location = mFirstAlwaysLocation != null
                    ? mFirstAlwaysLocation
                    : Location.create(context.file);
            context.report(
                    ISSUE,
                    location,
                    "Prefer `ifRoom` over `always`; reserve the `always` value for only the "
                            + "most important action items. There are already more than 2 "
                            + "actions set to `always`.");
        } else if (!mHasIfRoom) {
            Location location = mFirstAlwaysLocation != null
                    ? mFirstAlwaysLocation
                    : Location.create(context.file);
            context.report(
                    ISSUE,
                    location,
                    "Prefer `ifRoom` over `always`; reserve the `always` value for only the "
                            + "most important action items");
        }
    }

    // ---- SourceCodeScanner ----

    @Override
    @Nullable
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement resolved) {
        String name = reference.getResolvedName();
        if (name == null) {
            return;
        }
        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            mJavaAlwaysCount++;
            if (mFirstJavaAlwaysLocation == null) {
                mFirstJavaAlwaysLocation = context.getLocation(reference);
            }
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mJavaHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mJavaAlwaysCount > 0 && !mJavaHasIfRoom) {
            Location location = mFirstJavaAlwaysLocation != null
                    ? mFirstJavaAlwaysLocation
                    : Location.create(context.file);
            context.report(
                    ISSUE,
                    location,
                    "Prefer `SHOW_AS_ACTION_IF_ROOM` over `SHOW_AS_ACTION_ALWAYS`; reserve "
                            + "the `SHOW_AS_ACTION_ALWAYS` value for only the most important "
                            + "action items");
        }
    }
}