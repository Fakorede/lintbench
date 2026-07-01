package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;

public class AlwaysShowActionDetector extends ResourceXmlDetector {

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    private static final String EXPLANATION =
            "Using `showAsAction=\"always\"` in menu XML, or "
                    + "`MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation "
                    + "from the user interface style guide. Use `ifRoom` or the corresponding "
                    + "`MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior "
                    + "is roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                    + "items. Using it more than twice in the same menu is a bad idea.\n\n"
                    + "This check looks for menu XML files that contain more than two `always` "
                    + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                    + "it looks for projects that contain references to "
                    + "`MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to "
                    + "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.";

    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    EXPLANATION,
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private static final String ATTRIBUTE_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";

    private int mAlwaysCount;
    private int mIfRoomCount;
    private Location mFirstAlwaysLocation;

    private boolean mHasAlwaysReference;
    private boolean mHasIfRoomReference;
    private Location mAlwaysReferenceLocation;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return Arrays.asList(ATTRIBUTE_SHOW_AS_ACTION);
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
        mFirstAlwaysLocation = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (!ATTRIBUTE_SHOW_AS_ACTION.equals(attribute.getLocalName())) {
            return;
        }

        String value = attribute.getValue();
        if (value == null) {
            return;
        }

        boolean hasAlways = false;
        boolean hasIfRoom = false;
        for (String flag : value.split("\\|")) {
            String trimmed = flag.trim();
            if (VALUE_ALWAYS.equalsIgnoreCase(trimmed)) {
                hasAlways = true;
            } else if (VALUE_IF_ROOM.equalsIgnoreCase(trimmed)) {
                hasIfRoom = true;
            }
        }

        if (hasAlways) {
            mAlwaysCount++;
            if (mFirstAlwaysLocation == null) {
                mFirstAlwaysLocation = context.getLocation(attribute);
            }
        }
        if (hasIfRoom) {
            mIfRoomCount++;
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (mAlwaysCount > 2 || (mAlwaysCount > 0 && mIfRoomCount == 0)) {
            String message =
                    "Using `showAsAction=\"always\"` is discouraged; prefer `ifRoom`, "
                            + "and avoid using `always` more than twice in the same menu";
            context.report(ISSUE, mFirstAlwaysLocation, message);
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
        String name = referenced.getText();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            mHasAlwaysReference = true;
            if (mAlwaysReferenceLocation == null) {
                mAlwaysReferenceLocation = context.getLocation(reference);
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            mHasIfRoomReference = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasAlwaysReference && !mHasIfRoomReference && mAlwaysReferenceLocation != null) {
            String message =
                    "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` without a corresponding "
                            + "`MenuItem.SHOW_AS_ACTION_IF_ROOM` is discouraged";
            context.report(ISSUE, mAlwaysReferenceLocation, message);
        }
    }
}