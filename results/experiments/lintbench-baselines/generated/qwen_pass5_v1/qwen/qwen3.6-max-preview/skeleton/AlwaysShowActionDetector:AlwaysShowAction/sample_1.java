package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiElement;
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
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
                    "If `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.\n\n" +
                    "This check looks for menu XML files that contain more than two `always` actions, or some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
                    Category.USABILITY,
                    3,
                    Severity.WARNING,
                    IMPLEMENTATION);

    private int xmlAlwaysCount;
    private int xmlIfRoomCount;
    private Attr firstAlwaysAttr;

    private boolean javaHasAlways;
    private boolean javaHasIfRoom;
    private Location javaAlwaysLocation;

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
        xmlAlwaysCount = 0;
        xmlIfRoomCount = 0;
        firstAlwaysAttr = null;
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (firstAlwaysAttr != null && (xmlAlwaysCount > 2 || xmlIfRoomCount == 0)) {
            XmlContext xmlContext = (XmlContext) context;
            xmlContext.report(ISSUE, firstAlwaysAttr, xmlContext.getLocation(firstAlwaysAttr),
                    "Use `ifRoom` or `SHOW_AS_ACTION_IF_ROOM` instead of `always`");
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (javaHasAlways && !javaHasIfRoom && javaAlwaysLocation != null) {
            context.report(ISSUE, javaAlwaysLocation,
                    "Use `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
        }
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value.contains("always")) {
            xmlAlwaysCount++;
            if (firstAlwaysAttr == null) {
                firstAlwaysAttr = attribute;
            }
        }
        if (value.contains("ifRoom")) {
            xmlIfRoomCount++;
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
        String source = reference.asSourceString();
        if (source.endsWith("SHOW_AS_ACTION_ALWAYS")) {
            javaHasAlways = true;
            if (javaAlwaysLocation == null) {
                javaAlwaysLocation = context.getLocation(reference);
            }
        } else if (source.endsWith("SHOW_AS_ACTION_IF_ROOM")) {
            javaHasIfRoom = true;
        }
    }
}