package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
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
import com.intellij.psi.PsiJavaCodeReferenceElement;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends Detector
        implements Detector.XmlScanner, Detector.JavaPsiScanner {

    private static final String SHOW_AS_ACTION = "showAsAction";
    private static final String ALWAYS = "always";
    private static final String IF_ROOM = "ifRoom";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Prefer `ifRoom` instead of `always` for showAsAction",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.\n\n"
                    + "This check looks for menu XML files that contain more than two `always` actions, or some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE))
    );

    private final List<Location> mXmlAlwaysLocations = new ArrayList<>();
    private int mXmlIfRoomCount;

    private int mAlwaysCount;
    private int mIfRoomCount;
    private Location mFirstAlwaysLocation;

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public void visitReference(JavaContext context, JavaElementVisitor visitor,
            PsiJavaCodeReferenceElement reference) {
        String name = reference.getReferenceName();
        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            mAlwaysCount++;
            if (mFirstAlwaysLocation == null) {
                mFirstAlwaysLocation = context.getLocation(reference);
            }
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mIfRoomCount++;
        }
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        mAlwaysCount = 0;
        mIfRoomCount = 0;
        mFirstAlwaysLocation = null;
    }

    @Override
    public void afterCheckEachProject(Context context) {
        if (mAlwaysCount > 0 && mIfRoomCount == 0 && mFirstAlwaysLocation != null) {
            context.report(ISSUE, mFirstAlwaysLocation,
                    "Using `MenuItem.SHOW_AS_ACTION_ALWAYS` without `MenuItem.SHOW_AS_ACTION_IF_ROOM`; prefer `SHOW_AS_ACTION_IF_ROOM`");
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (context instanceof XmlContext) {
            resetXmlState();
        }
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String localName = attr.getLocalName();
            String name = attr.getName();

            if (!SHOW_AS_ACTION.equals(localName) && !SHOW_AS_ACTION.equals(name)) {
                continue;
            }

            String value = attr.getValue();
            if (value == null) {
                continue;
            }

            boolean isAlways = false;
            boolean isIfRoom = false;
            for (String part : value.split("\\|")) {
                String trimmed = part.trim();
                if (ALWAYS.equals(trimmed)) {
                    isAlways = true;
                } else if (IF_ROOM.equals(trimmed)) {
                    isIfRoom = true;
                }
            }

            if (isAlways) {
                mXmlAlwaysLocations.add(context.getLocation(attr));
            }
            if (isIfRoom) {
                mXmlIfRoomCount++;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (!(context instanceof XmlContext)) {
            return;
        }

        int alwaysCount = mXmlAlwaysLocations.size();
        if (alwaysCount > 0 && (alwaysCount > 2 || mXmlIfRoomCount == 0)) {
            Location location = mXmlAlwaysLocations.get(0);
            String message;
            if (alwaysCount > 2) {
                message = String.format(
                        "Menu has %1$d `showAsAction=\"always\"` items; using more than two is discouraged, use `ifRoom` instead",
                        alwaysCount);
            } else {
                message = String.format(
                        "Menu has %1$d `showAsAction=\"always\"` item(s) but no `showAsAction=\"ifRoom\"` items; consider using `ifRoom` instead",
                        alwaysCount);
            }
            context.report(ISSUE, location, message);
        }
    }

    private void resetXmlState() {
        mXmlAlwaysLocations.clear();
        mXmlIfRoomCount = 0;
    }
}