public class ObsoleteLayoutParamsDetector extends Detector implements Detector.XmlScanner {
    private static final String PREFIX_LAYOUT_PARAM = "layout_";
    private static final String ANDROID_NS = SdkConstants.ANDROID_URI;
    private static final Map<String, Set<String>> LAYOUT_PARAMS = new HashMap<>();
    static {
        Set<String> linearLayouts = new HashSet<>(Arrays.asList(
            "LinearLayout",
            "RadioGroup",
            "TableRow",
            "NumberPicker",
            "SearchView",
            "ZoomControls"
        ));
        LAYOUT_PARAMS.put("layout_weight", linearLayouts);

        Set<String> gravityLayouts = new HashSet<>(linearLayouts);
        gravityLayouts.addAll(Arrays.asList(
            "FrameLayout",
            "ScrollView",
            "HorizontalScrollView",
            "ViewAnimator",
            "ViewFlipper",
            "ViewSwitcher",
            "ImageSwitcher",
            "TextSwitcher",
            "CalendarView",
            "GridLayout"
        ));
        LAYOUT_PARAMS.put("layout_gravity", gravityLayouts);

        Set<String> tableRowAndGrid = new HashSet<>(Arrays.asList("TableRow", "GridLayout"));
        LAYOUT_PARAMS.put("layout_column", tableRowAndGrid);
        LAYOUT_PARAMS.put("layout_columnSpan", tableRowAndGrid);

        LAYOUT_PARAMS.put("layout_row", new HashSet<>(Arrays.asList("GridLayout")));
        LAYOUT_PARAMS.put("layout_rowSpan", new HashSet<>(Arrays.asList("GridLayout")));
        LAYOUT_PARAMS.put("layout_span", new HashSet<>(Arrays.asList("TableRow")));

        Set<String> relativeLayouts = new HashSet<>(Arrays.asList("RelativeLayout"));
        String[] relativeAttrs = {
            "layout_above",
            "layout_below",
            "layout_toLeftOf",
            "layout_toRightOf",
            "layout_toStartOf",
            "layout_toEndOf",
            "layout_alignBaseline",
            "layout_alignBottom",
            "layout_alignEnd",
            "layout_alignLeft",
            "layout_alignRight",
            "layout_alignStart",
            "layout_alignTop",
            "layout_alignWithParentIfMissing",
            "layout_alignParentBottom",
            "layout_alignParentEnd",
            "layout_alignParentLeft",
            "layout_alignParentRight",
            "layout_alignParentStart",
            "layout_alignParentTop",
            "layout_centerHorizontal",
            "layout_centerVertical",
            "layout_centerInParent"
        };
        for (String attr : relativeAttrs) {
            LAYOUT_PARAMS.put(attr, relativeLayouts);
        }
    }

    public static final Issue ISSUE = Issue.create(
        "ObsoleteLayoutParam",
        "Obsolete layout params",
        "The given layout_param is not defined for the given layout, meaning it has no "
            + "effect. This usually happens when you change the parent layout or move view "
            + "code around without updating the layout params. This will cause useless "
            + "attribute processing at runtime, and is misleading for others reading the "
            + "layout so the parameter should be removed.",
        Category.CORRECTNESS,
        4,
        Severity.WARNING,
        new Implementation(ObsoleteLayoutParamsDetector.class, Scope.RESOURCE_FILE_SCOPE)
    );

    @Override
    public boolean appliesTo(ResourceFolderType folderType) {
        return folderType == ResourceFolderType.LAYOUT;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        Node parentNode = element.getParentNode();
        if (parentNode == null || parentNode.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        Element parent = (Element) parentNode;
        String parentTag = getSimpleName(parent.getTagName());
        if (parentTag == null || isUnknownParent(parentTag)) {
            return;
        }

        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node node = attributes.item(i);
            if (node.getNodeType() != Node.ATTRIBUTE_NODE) continue;
            Attr attr = (Attr) node;
            if (!ANDROID_NS.equals(attr.getNamespaceURI())) continue;
            String name = attr.getLocalName();
            if (name == null) name = attr.getName();
            if (name == null) continue;
            if (!name.startsWith(PREFIX_LAYOUT_PARAM)) continue;
            if (name.equals("layout_width") || name.equals("layout_height")) continue;
            if (name.startsWith("layout_margin")) continue;

            Set<String> validParents = LAYOUT_PARAMS.get(name);
            if (validParents == null) continue;

            if (!validParents.contains(parentTag)) {
                String layouts = join(validParents);
                String message;
                if (validParents.size() == 1) {
                    message = String.format(
                        "The `%1$s` attribute is only defined for the `%2$s` layout, but it is used in a `%3$s` layout.",
                        attr.getName(), layouts, parentTag);
                } else {
                    message = String.format(
                        "The `%1$s` attribute is only defined for the following layouts: %2$s, but it is used in a `%3$s` layout.",
                        attr.getName(), layouts, parentTag);
                }
                context.report(ISSUE, attr, context.getLocation(attr), message);
            }
        }
    }

    private static String getSimpleName(String tagName) {
        if (tagName == null) return null;
        int index = tagName.lastIndexOf('.');
        return index == -1 ? tagName : tagName.substring(index + 1);
    }

    private static boolean isUnknownParent(String parentTag) {
        return "merge".equals(parentTag) || "include".equals(parentTag) || "fragment".equals(parentTag);
    }

    private static String join(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String s : set) {
            if (first) first = false;
            else sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }
}