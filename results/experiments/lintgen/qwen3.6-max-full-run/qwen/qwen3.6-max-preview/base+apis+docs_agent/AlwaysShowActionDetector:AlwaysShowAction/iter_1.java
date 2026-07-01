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
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

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
        "some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that " +
        "contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to " +
        "`MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
        Category.CORRECTNESS,
        5,
        Severity.WARNING,
        new Implementation(AlwaysShowActionDetector.class, Scope.JAVA_FILE, Scope.RESOURCE_FILE)
    );

    private static class JavaState {
        boolean hasIfRoom = false;
        final List<Location> alwaysLocations = new ArrayList<>();
    }

    private final Map<Project, JavaState> javaStates = new HashMap<>();

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(JavaContext context, UReferenceExpression reference, PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && "android.view.MenuItem".equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                JavaState state = javaStates.computeIfAbsent(context.getProject(), p -> new JavaState());
                if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    state.hasIfRoom = true;
                } else if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    state.alwaysLocations.add(context.getLocation(reference));
                }
            }
        }
    }

    @Override
    public void afterCheckEachProject(Context context) {
        Project project = context.getProject();
        JavaState state = javaStates.remove(project);
        if (state != null && !state.hasIfRoom && !state.alwaysLocations.isEmpty()) {
            for (Location location : state.alwaysLocations) {
                context.report(ISSUE, location, "Prefer `SHOW_AS_ACTION_IF_ROOM` instead of `SHOW_AS_ACTION_ALWAYS`");
            }
        }
    }

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public void visitDocument(XmlContext context, Document document) {
        int[] counts = new int[2]; // 0: always, 1: ifRoom
        List<Attr> alwaysAttrs = new ArrayList<>();
        countActions(document.getDocumentElement(), counts, alwaysAttrs);

        int alwaysCount = counts[0];
        int ifRoomCount = counts[1];

        if (alwaysCount > 2 || (alwaysCount > 0 && ifRoomCount == 0)) {
            String message = alwaysCount > 2
                ? "Prefer `ifRoom` instead of `always` (more than 2 always items)"
                : "Prefer `ifRoom` instead of `always` (no ifRoom items in menu)";
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, context.getLocation(attr), message);
            }
        }
    }

    private void countActions(Element element, int[] counts, List<Attr> alwaysAttrs) {
        if (element == null) return;

        NamedNodeMap attrs = element.getAttributes();
        if (attrs != null) {
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attrNode = attrs.item(i);
                if (attrNode.getNodeType() == Node.ATTRIBUTE_NODE) {
                    Attr attr = (Attr) attrNode;
                    if ("showAsAction".equals(attr.getLocalName())) {
                        String value = attr.getValue();
                        if (value != null) {
                            if (value.contains("always")) {
                                counts[0]++;
                                alwaysAttrs.add(attr);
                            }
                            if (value.contains("ifRoom")) {
                                counts[1]++;
                            }
                        }
                    }
                }
            }
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                countActions((Element) child, counts, alwaysAttrs);
            }
        }
    }
}