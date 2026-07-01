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
import com.android.tools.lint.detector.api.UastScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, UastScanner {

    public static final Issue ISSUE = Issue.create(
        "AlwaysShowAction",
        "Usage of showAsAction=always",
        "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
        "Java code is usually a deviation from the user interface style guide. Use `ifRoom` or " +
        "the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
        "If `always` is used sparingly there are usually no problems and behavior is " +
        "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
        "items. Using it more than twice in the same menu is a bad idea.\n\n" +
        "This check looks for menu XML files that contain more than two `always` " +
        "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
        "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
        "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(AlwaysShowActionDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE))
    );

    private int xmlAlwaysCount;
    private int xmlIfRoomCount;
    private Location xmlFirstAlwaysLocation;

    private final List<Location> javaAlwaysLocations = new ArrayList<>();
    private boolean javaHasIfRoom;

    @Override
    public void beforeCheckFile(Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlContext.getResourceFolderType() == ResourceFolderType.MENU) {
                xmlAlwaysCount = 0;
                xmlIfRoomCount = 0;
                xmlFirstAlwaysLocation = null;
            }
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlContext.getResourceFolderType() == ResourceFolderType.MENU) {
                if (xmlAlwaysCount > 2 || (xmlAlwaysCount > 0 && xmlIfRoomCount == 0)) {
                    String message = "Prefer \"ifRoom\" instead of \"always\"";
                    context.report(ISSUE, xmlFirstAlwaysLocation, message);
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String value = getShowAsActionValue(element);
        if (value == null) {
            return;
        }
        if (value.contains("always")) {
            xmlAlwaysCount++;
            if (xmlFirstAlwaysLocation == null) {
                Attr attr = getShowAsActionAttr(element);
                xmlFirstAlwaysLocation = attr != null ? context.getLocation(attr) : context.getLocation(element);
            }
        }
        if (value.contains("ifRoom")) {
            xmlIfRoomCount++;
        }
    }

    private Attr getShowAsActionAttr(Element element) {
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String name = attr.getLocalName();
            if (name == null) {
                name = attr.getNodeName();
            }
            if ("showAsAction".equals(name)) {
                return attr;
            }
        }
        return null;
    }

    private String getShowAsActionValue(Element element) {
        Attr attr = getShowAsActionAttr(element);
        return attr != null ? attr.getValue() : null;
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UReferenceExpression.class);
    }

    @Override
    public void visitReferenceExpression(JavaContext context, UReferenceExpression node) {
        PsiElement resolved = node.resolve();
        if (resolved instanceof PsiField) {
            PsiField field = (PsiField) resolved;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    javaAlwaysLocations.add(context.getLocation(node));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    javaHasIfRoom = true;
                }
            }
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        if (!javaAlwaysLocations.isEmpty() && !javaHasIfRoom) {
            String message = "Prefer SHOW_AS_ACTION_IF_ROOM instead of SHOW_AS_ACTION_ALWAYS";
            for (Location loc : javaAlwaysLocations) {
                context.report(ISSUE, loc, message);
            }
        }
        javaAlwaysLocations.clear();
        javaHasIfRoom = false;
    }
}