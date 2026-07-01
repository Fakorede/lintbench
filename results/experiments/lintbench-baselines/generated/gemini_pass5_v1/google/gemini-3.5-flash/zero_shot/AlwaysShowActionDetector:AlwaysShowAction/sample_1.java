package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.SourceCodeScanner;
import com.android.tools.lint.detector.api.Detector.XmlScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
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
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in " +
            "Java code is usually a deviation from the user interface style guide. Use `ifRoom` " +
            "or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n" +
            "\n" +
            "If `always` is used sparingly there are usually no problems and behavior is " +
            "roughly equivalent to `ifRoom` but with preference over other `ifRoom` " +
            "items. Using it more than twice in the same menu is a bad idea.\n" +
            "\n" +
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

    private Location firstAlwaysLocation;
    private boolean hasAlways;
    private boolean hasIfRoom;

    @Override
    public void beforeCheckProject(Context context) {
        hasAlways = false;
        hasIfRoom = false;
        firstAlwaysLocation = null;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }

        NodeList items = document.getElementsByTagName("item");
        int alwaysCount = 0;
        int ifRoomCount = 0;
        List<Element> alwaysElements = new ArrayList<>();

        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (node instanceof Element) {
                Element element = (Element) node;
                String showAsAction = null;
                Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "showAsAction");
                if (attr == null) {
                    attr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "showAsAction");
                }
                if (attr == null) {
                    attr = element.getAttributeNode("showAsAction");
                }
                if (attr != null) {
                    showAsAction = attr.getValue();
                }

                if (showAsAction != null) {
                    if (showAsAction.contains("always")) {
                        alwaysCount++;
                        alwaysElements.add(element);
                    }
                    if (showAsAction.contains("ifRoom")) {
                        ifRoomCount++;
                    }
                }
            }
        }

        if (alwaysCount > 2) {
            for (int i = 2; i < alwaysElements.size(); i++) {
                Element element = alwaysElements.get(i);
                Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "showAsAction");
                if (attr == null) {
                    attr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "showAsAction");
                }
                if (attr == null) {
                    attr = element.getAttributeNode("showAsAction");
                }
                Location location = attr != null ? context.getValueLocation(attr) : context.getLocation(element);
                context.report(ISSUE, element, location,
                        "Prefer \"`ifRoom`\" instead of \"`always`\"; more than two items has \"`always`\" in this menu.");
            }
        } else if (alwaysCount > 0 && ifRoomCount == 0) {
            for (Element element : alwaysElements) {
                Attr attr = element.getAttributeNodeNS(SdkConstants.ANDROID_URI, "showAsAction");
                if (attr == null) {
                    attr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, "showAsAction");
                }
                if (attr == null) {
                    attr = element.getAttributeNode("showAsAction");
                }
                Location location = attr != null ? context.getValueLocation(attr) : context.getLocation(element);
                context.report(ISSUE, element, location,
                        "Prefer \"`ifRoom`\" instead of \"`always`\" (unless you are also using `ifRoom` in the same menu)");
            }
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(JavaContext context, UReferenceExpression reference) {
        String name = reference.getResolvedName();
        if (name == null) {
            name = reference.getSourcePsi() != null ? reference.getSourcePsi().getText() : null;
        }
        if (name == null) {
            return;
        }

        if ("SHOW_AS_ACTION_ALWAYS".equals(name) || "SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            PsiElement resolved = reference.resolve();
            if (resolved instanceof PsiField) {
                PsiClass containingClass = ((PsiField) resolved).getContainingClass();
                if (containingClass != null && !"android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                    return;
                }
            }
            if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                hasAlways = true;
                if (firstAlwaysLocation == null) {
                    firstAlwaysLocation = context.getLocation(reference);
                }
            } else {
                hasIfRoom = true;
            }
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        if (hasAlways && !hasIfRoom && firstAlwaysLocation != null) {
            context.report(ISSUE, firstAlwaysLocation,
                    "Prefer `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS` " +
                    "(unless you are also using `SHOW_AS_ACTION_IF_ROOM` in the same project)");
        }
    }
}