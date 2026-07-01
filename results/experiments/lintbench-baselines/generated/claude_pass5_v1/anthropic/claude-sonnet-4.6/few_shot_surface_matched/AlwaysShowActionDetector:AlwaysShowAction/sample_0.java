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

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";

    // Per-file XML state
    private int mAlwaysCount;
    private boolean mHasIfRoom;
    private List<Location> mAlwaysLocations;

    // Project-level Java state
    private boolean mHasAlwaysReference;
    private boolean mHasIfRoomReference;
    private Location mAlwaysReferenceLocation;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_SHOW_AS_ACTION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysCount = 0;
        mHasIfRoom = false;
        mAlwaysLocations = new java.util.ArrayList<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysCount > 2) {
            // Report on all the always locations
            Location combined = null;
            for (int i = mAlwaysLocations.size() - 1; i >= 0; i--) {
                Location loc = mAlwaysLocations.get(i);
                if (combined == null) {
                    combined = loc;
                } else {
                    loc.setSecondary(combined);
                    combined = loc;
                }
            }
            context.report(
                    ISSUE,
                    combined != null ? combined : Location.create(context.file),
                    "Avoid using `showAsAction=\"always\"`; use `ifRoom` instead. "
                            + "This is a problem because there are more than 2 `always` actions.");
        } else if (mAlwaysCount > 0 && !mHasIfRoom) {
            Location loc =
                    mAlwaysLocations.isEmpty()
                            ? Location.create(context.file)
                            : mAlwaysLocations.get(0);
            context.report(
                    ISSUE,
                    loc,
                    "Prefer `showAsAction=\"ifRoom\"` instead of `\"always\"`. This is "
                            + "especially important when the menu has no `ifRoom` items.");
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }
        // The value may be a combination like "always|withText"
        for (String part : value.split("\\|")) {
            String trimmed = part.trim();
            if (VALUE_ALWAYS.equals(trimmed)) {
                mAlwaysCount++;
                mAlwaysLocations.add(context.getLocation(attribute));
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                mHasIfRoom = true;
            }
        }
    }

    // SourceCodeScanner

    @Override
    @Nullable
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull UReferenceExpression reference,
            @NonNull PsiElement referenced) {
        String name = reference.getResolvedName();
        if (name == null) {
            return;
        }
        // Verify the reference is on MenuItem
        String qualifiedName = null;
        com.intellij.psi.PsiMember member =
                (referenced instanceof com.intellij.psi.PsiMember)
                        ? (com.intellij.psi.PsiMember) referenced
                        : null;
        if (member != null && member.getContainingClass() != null) {
            qualifiedName = member.getContainingClass().getQualifiedName();
        }
        if (qualifiedName != null && !qualifiedName.equals(MENU_ITEM_CLASS)) {
            return;
        }

        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            if (!mHasAlwaysReference) {
                mHasAlwaysReference = true;
                mAlwaysReferenceLocation = context.getLocation(reference);
            }
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mHasIfRoomReference = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasAlwaysReference && !mHasIfRoomReference) {
            context.report(
                    ISSUE,
                    mAlwaysReferenceLocation != null
                            ? mAlwaysReferenceLocation
                            : Location.create(context.file),
                    "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead of "
                            + "`MenuItem.SHOW_AS_ACTION_ALWAYS`");
        }
    }
}