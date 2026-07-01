package com.android.tools.lint.checks;

import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code " +
            "is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding " +
            "`MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\n" +
            "If `always` is used sparingly there are usually no problems and behavior is roughly equivalent " +
            "to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same " +
            "menu is a bad idea.\n\n" +
            "This check looks for menu XML files that contain more than two `always` actions, or some " +
            "`always` actions and no `ifRoom` actions. In Java code, it looks for projects that contain " +
            "references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            5,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
    );

    private final Map<Project, List<Location>> mAlwaysLocations = new HashMap<>();
    private final Map<Project, Boolean> mHasIfRoom = new HashMap<>();

    @Override
    public void visitDocument(XmlContext context, Document document) {
        if (context.getResourceFolderType() != ResourceFolderType.MENU) {
            return;
        }

        List<Attr> alwaysAttrs = new ArrayList<>();
        List<Attr> ifRoomAttrs = new ArrayList<>();
        checkMenuNode(document.getDocumentElement(), alwaysAttrs, ifRoomAttrs);

        if (alwaysAttrs.size() > 2) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Prefer \"`ifRoom`\" instead of \"`always`\"; more than two \"`always`\" actions in a menu can clutter the action bar");
            }
        } else if (!alwaysAttrs.isEmpty() && ifRoomAttrs.isEmpty()) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr),
                        "Prefer \"`ifRoom`\" instead of \"`always`\" to avoid overlapping other important UI elements of the action bar");
            }
        }
    }

    private void checkMenuNode(Node node, List<Attr> alwaysAttrs, List<Attr> ifRoomAttrs) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            if ("item".equals(element.getTagName()) || "item".equals(element.getLocalName())) {
                org.w3c.dom.NamedNodeMap attributes = element.getAttributes();
                for (int i = 0; i < attributes.getLength(); i++) {
                    Node attrNode = attributes.item(i);
                    if (attrNode instanceof Attr) {
                        Attr attr = (Attr) attrNode;
                        String localName = attr.getLocalName();
                        if (localName == null) {
                            localName = attr.getName();
                            int colon = localName.indexOf(':');
                            if (colon != -1) {
                                localName = localName.substring(colon + 1);
                            }
                        }
                        if ("showAsAction".equals(localName)) {
                            String value = attr.getValue();
                            if (value.contains("always")) {
                                alwaysAttrs.add(attr);
                            } else if (value.contains("ifRoom")) {
                                ifRoomAttrs.add(attr);
                            }
                        }
                    }
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            checkMenuNode(children.item(i), alwaysAttrs, ifRoomAttrs);
        }
    }

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(JavaContext context, UReferenceExpression reference, PsiElement resolved) {
        String name = reference.getResolvedName();
        if (name == null) {
            name = reference.getIdentifier();
        }

        if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                    Project project = context.getProject();
                    List<Location> locations = mAlwaysLocations.get(project);
                    if (locations == null) {
                        locations = new ArrayList<>();
                        mAlwaysLocations.put(project, locations);
                    }
                    locations.add(context.getLocation(reference));
                }
            }
        } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
            if (resolved instanceof PsiField) {
                PsiField field = (PsiField) resolved;
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                    Project project = context.getProject();
                    mHasIfRoom.put(project, Boolean.TRUE);
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        List<Location> alwaysLocs = mAlwaysLocations.get(project);
        Boolean hasIfRoom = mHasIfRoom.get(project);
        if (alwaysLocs != null && !alwaysLocs.isEmpty() && (hasIfRoom == null || !hasIfRoom)) {
            for (Location location : alwaysLocs) {
                context.report(ISSUE, location,
                        "Prefer \"`SHOW_AS_ACTION_IF_ROOM`\" instead of \"`SHOW_AS_ACTION_ALWAYS`\" to avoid overlapping other important UI elements of the action bar");
            }
        }
    }
}