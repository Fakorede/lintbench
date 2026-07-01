package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of `showAsAction=always`",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
            "in Java code is usually a deviation from the user interface style guide. " +
            "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
            "If `always` is used sparingly there are usually no problems and behavior is " +
            "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
            "items. Using it more than twice in the same menu is a bad idea.\n\n" +
            "This check looks for menu XML files that contain more than two `always` " +
            "actions, or some `always` actions and no `ifRoom` actions. In Java code, " +
            "it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` " +
            "and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private final List<Element> xmlAlwaysElements = new ArrayList<>();
    private int xmlAlwaysCount = 0;
    private int xmlIfRoomCount = 0;

    private final List<Location> alwaysLocations = new ArrayList<>();
    private boolean hasIfRoom = false;

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public void beforeCheckFile(Context context) {
        if (context instanceof XmlContext) {
            xmlAlwaysElements.clear();
            xmlAlwaysCount = 0;
            xmlIfRoomCount = 0;
        }
    }

    @Override
    public void afterCheckFile(Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            if (xmlAlwaysCount > 2 || (xmlAlwaysCount > 0 && xmlIfRoomCount == 0)) {
                for (Element element : xmlAlwaysElements) {
                    Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "showAsAction");
                    if (attr == null) {
                        attr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "showAsAction");
                    }
                    Location location = attr != null ? xmlContext.getLocation(attr) : xmlContext.getLocation(element);
                    xmlContext.report(ISSUE, element, location, "Prefer \"`ifRoom`\" instead of \"`always`\"");
                }
            }
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("item");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String showAsAction = element.getAttributeNS(SdkConstants.ANDROID_URI, "showAsAction");
        if (showAsAction.isEmpty()) {
            showAsAction = element.getAttributeNS(SdkConstants.AUTO_URI, "showAsAction");
        }
        if (!showAsAction.isEmpty()) {
            if (showAsAction.contains("always")) {
                xmlAlwaysCount++;
                xmlAlwaysElements.add(element);
            }
            if (showAsAction.contains("ifRoom")) {
                xmlIfRoomCount++;
            }
        }
    }

    @Override
    public void beforeCheckEachProject(Context context) {
        alwaysLocations.clear();
        hasIfRoom = false;
    }

    @Override
    public void afterCheckEachProject(Context context) {
        if (!hasIfRoom && !alwaysLocations.isEmpty()) {
            for (Location location : alwaysLocations) {
                context.report(ISSUE, location, "Prefer `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(JavaContext context, UReferenceExpression reference, PsiElement referenced) {
        String name = null;
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                name = field.getName();
            }
        } else {
            String refName = reference.getResolvedName();
            if ("SHOW_AS_ACTION_ALWAYS".equals(refName) || "SHOW_AS_ACTION_IF_ROOM".equals(refName)) {
                name = refName;
            }
        }

        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            alwaysLocations.add(context.getLocation(reference));
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            hasIfRoom = true;
        }
    }
}