package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.JavaReference;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements Detector.XmlScanner, Detector.JavaScanner {

    public static final Issue ISSUE = Issue.create(
        "AlwaysShowAction",
        "Usage of showAsAction=always",
        "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
        "Java code is usually a deviation from the user interface style guide. Use `ifRoom` or " +
        "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
        "If `always` is used sparingly there are usually no problems and behavior is roughly " +
        "equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more " +
        "than twice in the same menu is a bad idea.\n\n" +
        "This check looks for menu XML files that contain more than two `always` actions, or " +
        "some `always` actions and no `ifRoom` actions. In Java code, it looks for projects " +
        "that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to " +
        "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
        Category.CORRECTNESS,
        4,
        Severity.WARNING,
        new Implementation(AlwaysShowActionDetector.class, Scope.JAVA_AND_RESOURCE_FILES)
    );

    private int xmlAlwaysCount;
    private int xmlIfRoomCount;
    private List<Location> xmlAlwaysLocations;

    private List<Location> javaAlwaysLocations;
    private int javaIfRoomCount;

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        javaAlwaysLocations = new ArrayList<>();
        javaIfRoomCount = 0;
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        if (!javaAlwaysLocations.isEmpty() && javaIfRoomCount == 0) {
            for (Location loc : javaAlwaysLocations) {
                context.report(ISSUE, loc, "Use `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
            }
        }
    }

    @Override
    public void beforeCheckFile(@NotNull Context context) {
        if (context instanceof XmlContext) {
            xmlAlwaysCount = 0;
            xmlIfRoomCount = 0;
            xmlAlwaysLocations = new ArrayList<>();
        }
    }

    @Override
    public void afterCheckFile(@NotNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlContext.getResourceFolderType() == ResourceFolderType.MENU) {
                if (xmlAlwaysCount > 2 || (xmlAlwaysCount > 0 && xmlIfRoomCount == 0)) {
                    for (Location loc : xmlAlwaysLocations) {
                        xmlContext.report(ISSUE, loc, "Use `ifRoom` instead of `always`");
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        if (context.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }

        String value = element.getAttributeNS("http://schemas.android.com/apk/res/android", "showAsAction");
        if (value == null || value.isEmpty()) {
            value = element.getAttributeNS("http://schemas.android.com/apk/res-auto", "showAsAction");
        }

        if (value != null && !value.isEmpty()) {
            String[] parts = value.split("\\|");
            boolean hasAlways = false;
            boolean hasIfRoom = false;
            for (String part : parts) {
                String trimmed = part.trim();
                if ("always".equals(trimmed)) {
                    hasAlways = true;
                } else if ("ifRoom".equals(trimmed)) {
                    hasIfRoom = true;
                }
            }

            if (hasAlways) {
                xmlAlwaysCount++;
                xmlAlwaysLocations.add(context.getLocation(element));
            }
            if (hasIfRoom) {
                xmlIfRoomCount++;
            }
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NotNull JavaContext context, @NotNull JavaReference reference) {
        String name = reference.getName();
        if (isMenuItemField(reference)) {
            if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                javaAlwaysLocations.add(context.getLocation(reference));
            } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                javaIfRoomCount++;
            }
        }
    }

    private static boolean isMenuItemField(@NotNull JavaReference reference) {
        PsiElement resolved = reference.resolve();
        if (resolved instanceof PsiField) {
            PsiClass containingClass = ((PsiField) resolved).getContainingClass();
            if (containingClass != null) {
                return "android.view.MenuItem".equals(containingClass.getQualifiedName());
            }
        }
        return false;
    }
}