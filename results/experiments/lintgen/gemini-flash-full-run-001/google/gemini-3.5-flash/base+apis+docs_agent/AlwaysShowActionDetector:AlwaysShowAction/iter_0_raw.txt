package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                + "Java code is usually a deviation from the user interface style guide. Use `ifRoom` "
                + "or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n"
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
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private final List<Location> alwaysLocations = new ArrayList<>();
    private boolean hasIfRoom = false;

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        alwaysLocations.clear();
        hasIfRoom = false;
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        if (!hasIfRoom && !alwaysLocations.isEmpty()) {
            for (Location location : alwaysLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Prefer using `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS` "
                            + "to ensure the action bar does not overflow on smaller screens"
                );
            }
        }
    }

    // ---- Implements XmlScanner ----

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("menu");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        Node parent = element.getParentNode();
        if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE && "item".equals(parent.getNodeName())) {
            // Skip submenus, they will be processed as part of the root menu traversal
            return;
        }

        List<Attr> alwaysAttrs = new ArrayList<>();
        int alwaysCount = 0;
        int ifRoomCount = 0;

        Queue<Element> queue = new LinkedList<>();
        queue.add(element);
        while (!queue.isEmpty()) {
            Element current = queue.poll();
            NodeList children = current.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) child;
                    String tagName = childElement.getTagName();
                    if ("item".equals(tagName)) {
                        Attr attr = childElement.getAttributeNodeNS("http://schemas.android.com/apk/res/android", "showAsAction");
                        if (attr == null) {
                            attr = childElement.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", "showAsAction");
                        }
                        if (attr == null) {
                            attr = childElement.getAttributeNode("showAsAction");
                        }
                        if (attr != null) {
                            String value = attr.getValue();
                            if (containsAction(value, "always")) {
                                alwaysCount++;
                                alwaysAttrs.add(attr);
                            }
                            if (containsAction(value, "ifRoom")) {
                                ifRoomCount++;
                            }
                        }
                    }
                    queue.add(childElement);
                }
            }
        }

        if (alwaysCount > 2) {
            for (Attr attr : alwaysAttrs) {
                context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Don't use `always` more than two times in the same menu; it can crowd the action bar"
                );
            }
        } else if (alwaysCount > 0 && ifRoomCount == 0) {
            for (Attr attr : alwaysAttrs) {
                context.report(
                        ISSUE,
                        context.getLocation(attr),
                        "Prefer using `ifRoom` instead of `always` to ensure the action bar does not overflow on smaller screens"
                );
            }
        }
    }

    private static boolean containsAction(@Nullable String value, @NonNull String action) {
        if (value == null) {
            return false;
        }
        for (String part : value.split("\\|")) {
            if (part.trim().equals(action)) {
                return true;
            }
        }
        return false;
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
}