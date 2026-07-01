package com.android.tools.lint.checks;

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
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UReference;
import org.w3c.dom.Attr;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlwaysShowActionDetector extends ResourceXmlDetector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Using showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                    + "Java code is usually a deviation from the user interface style guide. Use `ifRoom` or "
                    + "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
                    + "If `always` is used sparingly there are usually no problems and behavior is "
                    + "roughly equivalent to `ifRoom` but with preference over other `ifRoom` "
                    + "items. Using it more than twice in the same menu is a bad idea.\n\n"
                    + "This check looks for menu XML files that contain more than two `always` "
                    + "actions, or some `always` actions and no `ifRoom` actions. In Java code, "
                    + "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` "
                    + "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(AlwaysShowActionDetector.class, Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE));

    private int xmlAlwaysCount;
    private int xmlIfRoomCount;
    private Location xmlAlwaysLocation;

    private int javaAlwaysCount;
    private int javaIfRoomCount;
    private Location javaAlwaysLocation;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @NonNull
    @Override
    public Collection<String> getApplicableAttributes() {
        return Collections.singletonList("showAsAction");
    }

    @Override
    public void beforeCheckFile(@NonNull Context context) {
        xmlAlwaysCount = 0;
        xmlIfRoomCount = 0;
        xmlAlwaysLocation = null;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        String value = attribute.getValue();
        if (value != null) {
            if (value.contains("always")) {
                xmlAlwaysCount++;
                if (xmlAlwaysLocation == null) {
                    xmlAlwaysLocation = context.getLocation(attribute);
                }
            }
            if (value.contains("ifRoom")) {
                xmlIfRoomCount++;
            }
        }
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (xmlAlwaysLocation != null && (xmlAlwaysCount > 2 || (xmlAlwaysCount > 0 && xmlIfRoomCount == 0))) {
            context.report(ISSUE, xmlAlwaysLocation,
                    "Prefer `showAsAction=\"ifRoom\"` over `\"always\"` to avoid UI clutter");
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReference reference, @NonNull PsiElement referenced) {
        String name = reference.getReferenceName();
        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            javaAlwaysCount++;
            if (javaAlwaysLocation == null) {
                javaAlwaysLocation = context.getLocation(reference);
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            javaIfRoomCount++;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (javaAlwaysLocation != null && javaAlwaysCount > 0 && javaIfRoomCount == 0) {
            context.report(ISSUE, javaAlwaysLocation,
                    "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` over `SHOW_AS_ACTION_ALWAYS` to avoid UI clutter");
        }
    }
}