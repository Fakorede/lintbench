package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
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
        Category.CORRECTNESS,
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
        if (!alwaysLocations.isEmpty() && !hasIfRoom) {
            for (Location location : alwaysLocations) {
                context.report(ISSUE, location, "If you are using `SHOW_AS_ACTION_ALWAYS`, you should also use `SHOW_AS_ACTION_IF_ROOM` to ensure that items can be collapsed if there is not enough room.");
            }
        }
        alwaysLocations.clear();
        hasIfRoom = false;
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {
        File parent = context.file.getParentFile();
        if (parent != null) {
            String folderName = parent.getName();
            if (!folderName.equals("menu") && !folderName.startsWith("menu-")) {
                return;
            }
        } else {
            return;
        }

        List<Attr> alwaysAttrs = new ArrayList<>();
        List<Attr> ifRoomAttrs = new ArrayList<>();
        Element root = document.getDocumentElement();
        if (root != null) {
            checkElement(root, alwaysAttrs, ifRoomAttrs);
        }

        if (alwaysAttrs.size() > 2) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr),
                    "Do not use `always` more than twice in the same menu; it can crowd the action bar. Use `ifRoom` instead.");
            }
        } else if (!alwaysAttrs.isEmpty() && ifRoomAttrs.isEmpty()) {
            for (Attr attr : alwaysAttrs) {
                context.report(ISSUE, attr, context.getLocation(attr),
                    "If you are using `always`, you should also use `ifRoom` to ensure that items can be collapsed if there is not enough room.");
            }
        }
    }

    private void checkElement(@NonNull Element element, @NonNull List<Attr> alwaysAttrs, @NonNull List<Attr> ifRoomAttrs) {
        if ("item".equals(element.getTagName())) {
            NamedNodeMap attributes = element.getAttributes();
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
                        }
                        if (value.contains("ifRoom")) {
                            ifRoomAttrs.add(attr);
                        }
                    }
                }
            }
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                checkElement((Element) child, alwaysAttrs, ifRoomAttrs);
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
}