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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String MENU = "menu";
    private static final String ITEM = "item";
    private static final String SHOW_AS_ACTION = "showAsAction";
    private static final String ALWAYS = "always";
    private static final String IF_ROOM = "ifRoom";
    private static final String MENU_ITEM_CLASS = "android.view.MenuItem";

    private final List<Location> mAlwaysReferences = new ArrayList<>();
    private final List<Location> mIfRoomReferences = new ArrayList<>();

    public static final Issue ISSUE = Issue.create(
            "AlwaysShowAction",
            "Usage of showAsAction=always",
            "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in Java code is usually a deviation from the user interface style guide. Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n\nIf `always` is used sparingly there are usually no problems and behavior is roughly equivalent to `ifRoom` but with preference over other `ifRoom` items. Using it more than twice in the same menu is a bad idea.\n\nThis check looks for menu XML files that contain more than two `always` actions, or some `always` actions and no `ifRoom` actions. In Java code, it looks for projects that contain references to `MenuItem.SHOW_AS_ACTION_ALWAYS` and no references to `MenuItem.SHOW_AS_ACTION_IF_ROOM`.",
            Category.USABILITY,
            3,
            Severity.WARNING,
            new Implementation(
                    AlwaysShowActionDetector.class,
                    EnumSet.of(Scope.RESOURCE_FILE_SCOPE, Scope.JAVA_FILE_SCOPE)
            )
    );

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(MENU);
    }

    @Override
    public boolean appliesTo(@NotNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MENU;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        int alwaysCount = 0;
        int ifRoomCount = 0;

        NodeList items = element.getElementsByTagName(ITEM);
        for (int i = 0; i < items.getLength(); i++) {
            Node node = items.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element item = (Element) node;
            if (getParentMenu(item) != element) {
                continue;
            }
            String value = getShowAsActionValue(item);
            if (value == null) {
                continue;
            }
            for (String flag : value.split("\\|")) {
                String trimmed = flag.trim();
                if (ALWAYS.equals(trimmed)) {
                    alwaysCount++;
                } else if (IF_ROOM.equals(trimmed)) {
                    ifRoomCount++;
                }
            }
        }

        if (alwaysCount > 2) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Menu has " + alwaysCount + " showAsAction=\"always\" items; using more than two is not recommended"
            );
        } else if (alwaysCount > 0 && ifRoomCount == 0) {
            context.report(
                    ISSUE,
                    context.getLocation(element),
                    "Menu has showAsAction=\"always\" items but no showAsAction=\"ifRoom\" items"
            );
        }
    }

    private static Element getParentMenu(@NotNull Element element) {
        Node parent = element.getParentNode();
        while (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
            Element parentElement = (Element) parent;
            if (MENU.equals(parentElement.getTagName())) {
                return parentElement;
            }
            parent = parent.getParentNode();
        }
        return null;
    }

    private static String getShowAsActionValue(@NotNull Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node node = attributes.item(i);
            if (node.getNodeType() != Node.ATTRIBUTE_NODE) {
                continue;
            }
            Attr attr = (Attr) node;
            String localName = attr.getLocalName();
            String name = attr.getName();
            if (SHOW_AS_ACTION.equals(localName)
                    || (localName == null && SHOW_AS_ACTION.equals(name))) {
                return attr.getValue();
            }
        }
        return null;
    }

    @Override
    @NotNull
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList("SHOW_AS_ACTION_ALWAYS", "SHOW_AS_ACTION_IF_ROOM");
    }

    @Override
    public void visitReference(@NotNull JavaContext context, @NotNull UReferenceExpression reference,
            @NotNull PsiElement referenced) {
        if (referenced instanceof PsiField) {
            PsiField field = (PsiField) referenced;
            PsiClass containingClass = field.getContainingClass();
            if (containingClass != null && MENU_ITEM_CLASS.equals(containingClass.getQualifiedName())) {
                String name = field.getName();
                if ("SHOW_AS_ACTION_ALWAYS".equals(name)) {
                    mAlwaysReferences.add(context.getLocation(reference));
                } else if ("SHOW_AS_ACTION_IF_ROOM".equals(name)) {
                    mIfRoomReferences.add(context.getLocation(reference));
                }
            }
        }
    }

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mAlwaysReferences.clear();
        mIfRoomReferences.clear();
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        if (!mAlwaysReferences.isEmpty() && mIfRoomReferences.isEmpty()) {
            context.report(
                    ISSUE,
                    mAlwaysReferences.get(0),
                    "This project references MenuItem.SHOW_AS_ACTION_ALWAYS but not MenuItem.SHOW_AS_ACTION_IF_ROOM"
            );
        }
    }
}