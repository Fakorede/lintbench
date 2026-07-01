String name = attribute.getLocalName();
String uri = attribute.getNamespaceURI();
String value = attribute.getValue();
...
if (ON_CLICK.equals(name) && ANDROID_URI.equals(uri)) { ... }
else if (TOOLS_CONTEXT.equals(name) && TOOLS_URI.equals(uri)) { ... }