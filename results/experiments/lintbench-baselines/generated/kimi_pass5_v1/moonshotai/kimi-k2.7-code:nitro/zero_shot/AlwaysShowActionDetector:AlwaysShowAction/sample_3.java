package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedField;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaElementVisitor;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.List;

import lombok.ast.Node;

public class AlwaysShowActionDetector extends Detector
        implements Detector.XmlScanner, Detector.JavaScanner {

    private static final String SHOW_AS_ACTION_ALWAYS = "always";
    private static final String SHOW_AS_ACTION_IF_ROOM = "ifRoom";

    private static final String MENU_ITEM_CLASS_NAME = "MenuItem";
    private static final String MENU_ITEM_PACKAGE = "android.view";
    private static final String FIELD_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String FIELD_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    private boolean mHasAlways;
    private boolean mHasIfRoom;
    private Location mFirstAlwaysLocation;
    private JavaContext mFirstAlwaysContext;

    public static final Issue ISSUE = Issue.create(
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
                    + "actions, or some `always` actions and no `ifRoom` actions. In Java code, it "
                    + "looks for projects that contain references to "
                    + "`MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to "
                    + "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    Scope.RESOURCE_FILE_SCOPE,
                    Scope.JAVA_FILE_SCOPE));

    // ---- XML scanning ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        NodeList items = document.getElementsByTagName(SdkConstants.TAG_ITEM);
        int alwaysCount = 0;
        int ifRoomCount = 0;
        Element firstAlways = null;

        for (int i = 0, n = items.getLength(); i < n; i++) {
            Element item = (Element) items.item(i);
            String value = getShowAsAction(item);
            if (value == null || value.isEmpty()) {
                continue;
            }

            if (value.contains(SHOW_AS_ACTION_ALWAYS)) {
                alwaysCount++;
                if (firstAlways == null) {
                    firstAlways = item;
                }
            }
            if (value.contains(SHOW_AS_ACTION_IF_ROOM)) {
                ifRoomCount++;
            }
        }

        if (alwaysCount > 2 && firstAlways != null) {
            context.report(ISSUE, firstAlways, context.getLocation(firstAlways),
                    "Menu has " + alwaysCount + " items using `showAsAction=\"always\"`; "
                            + "using more than two is a bad idea, use "
                            + "`showAsAction=\"ifRoom\"` instead");
        } else if (alwaysCount > 0 && ifRoomCount == 0 && firstAlways != null) {
            context.report(ISSUE, firstAlways, context.getLocation(firstAlways),
                    "Menu has items using `showAsAction=\"always\"` but no items using "
                            + "`showAsAction=\"ifRoom\"`; prefer `ifRoom`");
        }
    }

    @Nullable
    private static String getShowAsAction(@NonNull Element item) {
        String value = item.getAttributeNS(
                SdkConstants.ANDROID_URI, SdkConstants.ATTR_SHOW_AS_ACTION);
        if (value == null || value.isEmpty()) {
            value = item.getAttributeNS(
                    SdkConstants.AUTO_URI, SdkConstants.ATTR_SHOW_AS_ACTION);
        }
        return value;
    }

    // ---- Java scanning ----

    @Override
    @NonNull
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(FIELD_ALWAYS, FIELD_IF_ROOM);
    }

    @Override
    public void visitReference(@NonNull JavaContext context,
            @NonNull JavaElementVisitor visitor,
            @NonNull Node reference) {
        ResolvedNode resolved = context.resolve(reference);
        if (!(resolved instanceof ResolvedField)) {
            return;
        }

        ResolvedField field = (ResolvedField) resolved;
        ResolvedClass containing = field.getContainingClass();
        if (containing == null
                || !MENU_ITEM_PACKAGE.equals(containing.getPackageName())
                || !MENU_ITEM_CLASS_NAME.equals(containing.getSimpleName())) {
            return;
        }

        String name = field.getName();
        if (FIELD_ALWAYS.equals(name)) {
            mHasAlways = true;
            if (mFirstAlwaysLocation == null) {
                mFirstAlwaysLocation = context.getLocation(reference);
                mFirstAlwaysContext = context;
            }
        } else if (FIELD_IF_ROOM.equals(name)) {
            mHasIfRoom = true;
        }
    }

    // ---- Project-wide reporting ----

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mHasAlways = false;
        mHasIfRoom = false;
        mFirstAlwaysLocation = null;
        mFirstAlwaysContext = null;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mHasAlways && !mHasIfRoom && mFirstAlwaysContext != null) {
            mFirstAlwaysContext.report(ISSUE, mFirstAlwaysLocation,
                    "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` without also using "
                            + "`MenuItem.SHOW_AS_ACTION_IF_ROOM` is a bad idea; prefer `ifRoom`");
        }
    }
}