package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                    + "Java code is usually a deviation from the user interface style guide. Use "
                    + "`ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
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
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private final List<Location> alwaysLocations = new ArrayList<>();
    private boolean hasIfRoom = false;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        alwaysLocations.clear();
        hasIfRoom = false;
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (!hasIfRoom && !alwaysLocations.isEmpty()) {
            for (Location location : alwaysLocations) {
                context.report(ISSUE, location, "Prefer \"ifRoom\" instead of \"always\"");
            }
        }
    }

    // ---- Implements SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NonNull JavaContext context, @NonNull UReferenceExpression reference, @NonNull PsiElement resolved) {
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    alwaysLocations.add(context.getLocation(reference));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    hasIfRoom = true;
                }
            }
        }
    }

    // ---- Implements XmlScanner ----

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }

        NodeList items = document.getElementsByTagName("item");
        List<Attr> alwaysAttrs = new ArrayList<>();
        boolean xmlHasIfRoom = false;

        for (int i = 0; i < items.getLength(); i++) {
            Node item = items.item(i);
            if (item instanceof Element) {
                Element element = (Element) item;
                Attr attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "showAsAction");
                if (attr == null) {
                    attr = element.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", "showAsAction");
                }
                if (attr == null) {
                    attr = element.getAttributeNode("showAsAction");
                }

                if (attr != null) {
                    String value = attr.getValue();
                    if (value != null) {
                        if (value.contains("always")) {
                            alwaysAttrs.add(attr);
                        }
                        if (value.contains("ifRoom")) {
                            xmlHasIfRoom = true;
                        }
                    }
                }
            }
        }

        if (alwaysAttrs.size() > 2) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr), "Do not use \"always\" more than two times in the same menu (prefer \"ifRoom\")");
            }
        } else if (!alwaysAttrs.isEmpty() && !xmlHasIfRoom) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr), "Prefer \"ifRoom\" instead of \"always\"");
            }
        }
    }
}