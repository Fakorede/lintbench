package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
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
import com.intellij.psi.PsiField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

/**
 * Detector for the AlwaysShowAction issue.
 *
 * <p>Checks for usage of showAsAction="always" in menu XML files and
 * MenuItem.SHOW_AS_ACTION_ALWAYS in Java/Kotlin source code.
 */
public class AlwaysShowActionDetector extends Detector implements XmlScanner, SourceCodeScanner {

    /** The main issue discovered by this detector */
    public static final Issue ISSUE =
            Issue.create(
                    "AlwaysShowAction",
                    "Usage of `showAsAction=always`",
                    "Using `showAsAction=\"always\"` in menu XML, or `MenuItem.SHOW_AS_ACTION_ALWAYS` in "
                            + "Java code is usually a deviation from the user interface style guide. "
                            + "Use `ifRoom` or the corresponding `MenuItem.SHOW_AS_ACTION_IF_ROOM` instead.\n"
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
                    6,
                    Severity.WARNING,
                    new Implementation(
                            AlwaysShowActionDetector.class,
                            EnumSet.of(Scope.RESOURCE_FILE, Scope.ALL_JAVA_FILES)));

    private static final String ATTR_SHOW_AS_ACTION = "showAsAction";
    private static final String VALUE_ALWAYS = "always";
    private static final String VALUE_IF_ROOM = "ifRoom";
    private static final String MENU_ITEM_TAG = "item";

    private static final String SHOW_AS_ACTION_ALWAYS = "SHOW_AS_ACTION_ALWAYS";
    private static final String SHOW_AS_ACTION_IF_ROOM = "SHOW_AS_ACTION_IF_ROOM";
    private static final String MENU_ITEM_CLASS = "MenuItem";

    // Java/Kotlin tracking across files
    private final List<Location> mAlwaysLocations = new ArrayList<>();
    private boolean mHasIfRoom = false;

    // -----------------------------------------------------------------------
    // XmlScanner
    // -----------------------------------------------------------------------

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(MENU_ITEM_TAG);
    }

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull org.w3c.dom.Document document) {
        // We handle per-document analysis in visitElement accumulation + afterCheckFile
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Handled via afterCheckFile by accumulating per-file; but we do per-file analysis
        // in afterCheckFile by scanning the document directly. So nothing needed here.
    }

    @Override
    public void afterCheckFile(@NonNull Context context) {
        if (context instanceof XmlContext) {
            XmlContext xmlContext = (XmlContext) context;
            // Only process menu resource files
            if (!isMenuFile(xmlContext)) {
                return;
            }
            analyzeMenuDocument(xmlContext);
        }
    }

    private boolean isMenuFile(@NonNull XmlContext context) {
        String folderName = context.file.getParentFile() != null
                ? context.file.getParentFile().getName()
                : "";
        return folderName.startsWith("menu");
    }

    private void analyzeMenuDocument(@NonNull XmlContext context) {
        org.w3c.dom.Document document = context.document;
        if (document == null) {
            return;
        }

        NodeList items = document.getElementsByTagName(MENU_ITEM_TAG);
        if (items == null) {
            return;
        }

        List<Attr> alwaysAttrs = new ArrayList<>();
        boolean hasIfRoom = false;

        for (int i = 0; i < items.getLength(); i++) {
            org.w3c.dom.Node node = items.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element item = (Element) node;
            NamedNodeMap attrs = item.getAttributes();
            if (attrs == null) {
                continue;
            }

            // Check both namespaced and non-namespaced variants
            Attr showAsAction = findShowAsActionAttr(attrs);
            if (showAsAction == null) {
                continue;
            }

            String value = showAsAction.getValue();
            if (value == null) {
                continue;
            }

            // The value can be pipe-separated flags, e.g. "always|withText"
            String[] flags = value.split("\\|");
            boolean itemAlways = false;
            boolean itemIfRoom = false;
            for (String flag : flags) {
                String trimmed = flag.trim();
                if (VALUE_ALWAYS.equals(trimmed)) {
                    itemAlways = true;
                } else if (VALUE_IF_ROOM.equals(trimmed)) {
                    itemIfRoom = true;
                }
            }

            if (itemAlways) {
                alwaysAttrs.add(showAsAction);
            }
            if (itemIfRoom) {
                hasIfRoom = true;
            }
        }

        if (alwaysAttrs.isEmpty()) {
            return;
        }

        // Report if more than 2 always actions, or if there are always actions but no ifRoom
        if (alwaysAttrs.size() > 2) {
            // Report on each always attribute
            for (Attr attr : alwaysAttrs) {
                context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "Prefer `ifRoom` over `always`; see ActionBar documentation");
            }
        } else if (!hasIfRoom) {
            for (Attr attr : alwaysAttrs) {
                context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "Prefer `ifRoom` over `always`; see ActionBar documentation");
            }
        }
    }

    private Attr findShowAsActionAttr(@NonNull NamedNodeMap attrs) {
        // Try the app namespace first, then no namespace, then android namespace
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            String localName = attr.getLocalName();
            if (localName == null) {
                localName = attr.getName();
                // Strip prefix if present
                int colon = localName.indexOf(':');
                if (colon >= 0) {
                    localName = localName.substring(colon + 1);
                }
            }
            if (ATTR_SHOW_AS_ACTION.equals(localName)) {
                return attr;
            }
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // SourceCodeScanner
    // -----------------------------------------------------------------------

    @Override
    public List<String> getApplicableReferenceNames() {
        return Arrays.asList(SHOW_AS_ACTION_ALWAYS, SHOW_AS_ACTION_IF_ROOM);
    }

    @Override
    public void visitReference(
            @NonNull JavaContext context,
            @NonNull USimpleNameReferenceExpression reference,
            @NonNull PsiField resolved) {
        String name = resolved.getName();
        if (name == null) {
            return;
        }

        // Verify the field is from MenuItem
        String containingClassName = resolved.getContainingClass() != null
                ? resolved.getContainingClass().getName()
                : null;
        if (!MENU_ITEM_CLASS.equals(containingClassName)) {
            // Also accept android.view.MenuItem
            String qualifiedName = resolved.getContainingClass() != null
                    ? resolved.getContainingClass().getQualifiedName()
                    : null;
            if (qualifiedName == null || !qualifiedName.endsWith("MenuItem")) {
                return;
            }
        }

        if (SHOW_AS_ACTION_ALWAYS.equals(name)) {
            Location location = context.getLocation((UElement) reference);
            mAlwaysLocations.add(location);
        } else if (SHOW_AS_ACTION_IF_ROOM.equals(name)) {
            mHasIfRoom = true;
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (!mAlwaysLocations.isEmpty() && !mHasIfRoom) {
            for (Location location : mAlwaysLocations) {
                context.report(
                        ISSUE,
                        location,
                        "Prefer `SHOW_AS_ACTION_IF_ROOM` over `SHOW_AS_ACTION_ALWAYS`; "
                                + "see ActionBar documentation");
            }
        }
    }
}