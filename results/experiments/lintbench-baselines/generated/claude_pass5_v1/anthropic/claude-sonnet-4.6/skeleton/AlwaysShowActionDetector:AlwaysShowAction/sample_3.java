package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector {

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
                            + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
                            + "\n"
                            + "If `always` is used sparingly there are usually no problems and behavior is "
                            + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                            + "items. Using it more than twice in the same menu is a bad idea.\n"
                            + "\n"
                            + "This check looks for menu XML files that contain more than two `always` "
                            + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                            + "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                            + "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    /** Number of always actions in the current XML file */
    private int mAlwaysCount;

    /** Number of ifRoom actions in the current XML file */
    private int mIfRoomCount;

    /** Locations of always actions in the current XML file */
    private List<Location> mAlwaysLocations;

    /** Whether we've seen SHOW_AS_ACTION_ALWAYS in Java code */
    private boolean mHasAlwaysInJava;

    /** Whether we've seen SHOW_AS_ACTION_IF_ROOM in Java code */
    private boolean mHasIfRoomInJava;

    /** Location of the first SHOW_AS_ACTION_ALWAYS reference in Java code */
    private Location mJavaAlwaysLocation;

    public AlwaysShowActionDetector() {}

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList(ATTR_SHOW_AS_ACTION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
        mAlwaysLocations = new ArrayList<>();
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysCount > 2 || (mAlwaysCount > 0 && mIfRoomCount == 0)) {
            if (!mAlwaysLocations.isEmpty()) {
                Location location = mAlwaysLocations.get(0);
                // Chain secondary locations
                for (int i = 1; i < mAlwaysLocations.size(); i++) {
                    location.withSecondary(mAlwaysLocations.get(i), "Also used here");
                }
                String message;
                if (mAlwaysCount > 2) {
                    message =
                            "Prefer \"ifRoom\" instead of \"always\"; reserve the \"always\" "
                                    + "option for only the most important action items, and use no "
                                    + "more than 2. There are "
                                    + mAlwaysCount
                                    + " \"always\" actions in this menu.";
                } else {
                    message =
                            "Prefer \"ifRoom\" instead of \"always\"; reserve the \"always\" "
                                    + "option for only the most important action items. There are no "
                                    + "\"ifRoom\" actions.";
                }
                context.report(ISSUE, location, message);
            }
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasAlwaysInJava && !mHasIfRoomInJava && mJavaAlwaysLocation != null) {
            context.report(
                    ISSUE,
                    mJavaAlwaysLocation,
                    "Prefer `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        // The value can be a combination like "always|withText"
        boolean hasAlways = false;
        boolean hasIfRoom = false;
        for (String part : value.split("\\|")) {
            String trimmed = part.trim();
            if (VALUE_ALWAYS.equals(trimmed)) {
                hasAlways = true;
            } else if (VALUE_IF_ROOM.equals(trimmed)) {
                hasIfRoom = true;
            }
        }

        if (hasAlways) {
            mAlwaysCount++;
            mAlwaysLocations.add(context.getLocation(attribute));
        }
        if (hasIfRoom) {
            mIfRoomCount++;
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        List<String> names = new ArrayList<>();
        names.add(SHOW_AS_ACTION_ALWAYS);
        names.add(SHOW_AS_ACTION_IF_ROOM);
        return names;
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

        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            mHasAlwaysInJava = true;
            if (mJavaAlwaysLocation == null) {
                mJavaAlwaysLocation = context.getLocation(reference);
            }
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mHasIfRoomInJava = true;
        }
    }
}