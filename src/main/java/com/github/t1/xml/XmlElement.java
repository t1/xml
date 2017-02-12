package com.github.t1.xml;

import com.google.common.collect.ImmutableList;
import lombok.*;
import org.w3c.dom.CharacterData;
import org.w3c.dom.*;
import org.w3c.dom.ls.*;

import javax.xml.xpath.*;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

import static javax.xml.xpath.XPathConstants.*;

@EqualsAndHashCode
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class XmlElement {
    public interface XmlPosition {
        void add(XmlElement node, XmlElement relativeTo);
    }

    public static XmlPosition atBegin() {
        return (node, relativeTo) -> {
            List<XmlElement> siblings = relativeTo.getChildNodes();
            if (siblings.isEmpty()) {
                relativeTo.addIndent();
                relativeTo.append(node.element);
            } else {
                XmlElement reference = siblings.get(0);
                relativeTo.element.insertBefore(node.element, reference.element);
                relativeTo.element.insertBefore(node.createText(relativeTo.indentString()), reference.element);
            }
        };
    }

    public static XmlPosition atEnd() {
        return (node, relativeTo) -> {
            relativeTo.addIndent();
            relativeTo.append(node.element);
        };
    }

    public static XmlPosition before(String xpath) {
        return (node, relativeTo) -> {
            XmlElement reference = relativeTo.getXPathElement(xpath);
            relativeTo.element.insertBefore(node.element, reference.element);
            relativeTo.element.insertBefore(node.createText(relativeTo.indentString()), reference.element);
        };
    }


    private final XmlElement parent;
    protected final Element element;
    private final int indent;

    private volatile String indentString;

    /** The text before the closing tag. Everything else goes before this. */
    private Text finalText;

    protected Document document() { return element.getOwnerDocument(); }

    public String getName() { return element.getNodeName(); }

    public XmlElement assertName(String expectedName) {
        if (!expectedName.equals(getName())) {
            throw new AssertionError("expected element name '" + expectedName + "' but found '" + getName() + "'");
        }
        return this;
    }

    public Path getPath() { return buildPath(element, Paths.get("/")); }

    private Path buildPath(Node e, Path out) {
        Node parentNode = e.getParentNode();
        if (parentNode != null && parentNode instanceof Element)
            out = buildPath(parentNode, out);
        return out.resolve(e.getNodeName());
    }

    public boolean hasAttribute(String name) { return !element.getAttribute(name).isEmpty(); }

    public String getAttribute(String name) { return element.getAttribute(name); }

    public Optional<String> value() {
        if (elements().isEmpty()) {
            String text = element.getTextContent();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        }
        return Optional.empty();
    }

    public void value(String value) { element.setTextContent(value); }

    public List<XmlElement> elements() { return list(element.getChildNodes()); }

    private List<XmlElement> list(NodeList childNodes) {
        ImmutableList.Builder<XmlElement> result = ImmutableList.builder();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element) {
                Element element = (Element) child;
                result.add(newChildXmlElement(element));
            }
        }
        return result.build();
    }

    private XmlElement newChildXmlElement(Element e) { return new XmlElement(this, e, indent + 1); }

    public ImmutableList<Path> elementPaths() {
        ImmutableList.Builder<Path> result = ImmutableList.builder();
        for (XmlElement element : getChildNodes()) {
            result.add(element.getPath());
        }
        return result.build();
    }

    private List<XmlElement> getChildNodes() {
        ImmutableList.Builder<XmlElement> result = ImmutableList.builder();
        addChildNodes(element.getChildNodes(), result);
        return result.build();
    }

    private void addChildNodes(NodeList childNodes, ImmutableList.Builder<XmlElement> result) {
        list(childNodes).forEach(e -> {
            result.add(e);
            addChildNodes(e.element.getChildNodes(), result);
        });
    }

    public XmlElement getXPathElement(String xpath) {
        List<XmlElement> list = find(xpath);
        if (list.isEmpty())
            throw new IllegalArgumentException("no element found: " + xpath);
        if (list.size() > 1)
            throw new IllegalArgumentException("xpath expression resolves to " + list.size() + " elements, "
                    + "not a single element: " + xpath);
        return list.get(0);
    }

    @SneakyThrows(XPathExpressionException.class)
    public List<XmlElement> find(String xpath) {
        XPathExpression expr = XPathFactory.newInstance().newXPath().compile(xpath);
        return list((NodeList) expr.evaluate(element, NODESET));
    }

    public Optional<XmlElement> getOptionalElement(Path path) {
        Element node = element;
        for (Path pathElement : path) {
            NodeList elements = node.getElementsByTagName(pathElement.toString());
            if (elements.getLength() == 0)
                return Optional.empty();
            if (elements.getLength() > 1)
                throw new IllegalArgumentException("found " + elements.getLength() + " elements '" + pathElement
                        + "' in '" + getPath() + "'");
            node = (Element) elements.item(0);
        }
        return Optional.ofNullable(node).map(e -> new XmlElement(this, e, indent));
    }

    public boolean hasChildElement(Path path) {
        return getOptionalElement(path).filter(e -> hasChildElements(e.element)).isPresent();
    }

    private boolean hasChildElements(Element element) {
        NodeList childNodes = element.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node instanceof Element) {
                return true;
            }
        }
        return false;
    }

    /** Gets the first element with that name or creates one, if it doesn't exist */
    public XmlElement getOrCreateElement(Path path) {
        XmlElement out = this;
        for (Path item : path) {
            out = out.getOrCreateElement(item.toString());
        }
        return out;
    }

    public XmlElement getOrCreateElement(String name) { return getOrCreateElement(name, atEnd()); }

    public XmlElement getOrCreateElement(String name, XmlPosition position) {
        NodeList list = element.getElementsByTagName(name);
        if (list.getLength() >= 1)
            return newChildXmlElement((Element) list.item(0));
        return addElement(name, position);
    }

    public XmlElement addElement(Path path) {
        XmlElement out = this;
        for (Path item : path) {
            out = out.addElement(item.toString());
        }
        return out;
    }

    public XmlElement addElement(String name) { return addElement(name, atEnd()); }

    public XmlElement addElement(String name, XmlPosition position) {
        XmlElement node = newChildXmlElement(document().createElement(name));
        position.add(node, this);
        return node;
    }

    private void append(Node node) { element.insertBefore(node, finalText()); }

    private Text finalText() {
        if (finalText == null) {
            String finalIndent = indentString(indent - 1);
            Node last = element.getLastChild();
            if (last instanceof Text && finalIndent.equals(((CharacterData) last).getData())) {
                finalText = (Text) last;
            } else {
                finalText = createText(finalIndent);
                element.insertBefore(finalText, null);
            }
        }
        return finalText;
    }

    private void addIndent() { element.insertBefore(createText(indentString()), finalText()); }

    private String indentString() {
        if (indentString == null)
            this.indentString = indentString(indent);
        return indentString;
    }

    private String indentString(int n) {
        StringBuilder builder = new StringBuilder("\n");
        for (int i = 0; i < n; i++) {
            builder.append("    ");
        }
        return builder.toString();
    }

    private Text createText(String text) { return document().createTextNode(text); }

    public XmlElement setAttribute(String name, String value) {
        if (value == null)
            element.removeAttribute(name);
        else
            element.setAttribute(name, value);
        return this;
    }

    public XmlElement addComment(String text) {
        addIndent();
        append(document().createComment(" " + text + " "));
        return this;
    }

    public XmlElement nl() { return addText("\n"); }

    public XmlElement addText(String string) {
        // we don't use finalText here, as this may be an element without linebreaks
        // i.e. finalText may be null so the new text is inserted before the closing tag
        element.insertBefore(createText(string), finalText);
        return this;
    }

    public URI uri() { return URI.create(parent.uri() + uriDelimiter() + getName() + id()); }

    private String uriDelimiter() { return (parent.parent == null) ? "#" : "/"; }

    private String id() { return hasAttribute("id") ? (";id=" + getAttribute("id")) : ""; }

    @Override
    public String toString() {
        return getClass().getSimpleName() //
                + "[" + document().getDocumentURI() + "]"
                + "[" + getName() + (hasAttribute("id") ? ("@" + getAttribute("id")) : "") + "]\n" //
                + toXmlString();
    }

    public String toXmlString() {
        StringWriter out = new StringWriter();
        writeTo(out);
        return out.toString();
    }

    public void writeTo(Writer writer) {
        serializer().write(element, createOutput(writer));
        nl(writer);
    }

    protected LSSerializer serializer() { return domLs().createLSSerializer(); }

    protected DOMImplementationLS domLs() {
        DOMImplementationLS domLs = (DOMImplementationLS) (document().getImplementation()).getFeature("LS", "3.0");
        if (domLs == null)
            throw new UnsupportedOperationException("dom load and save not supported");
        return domLs;
    }

    private LSOutput createOutput(Writer writer) {
        LSOutput output = domLs().createLSOutput();
        output.setCharacterStream(writer);
        return output;
    }

    private void nl(Writer writer) {
        try {
            writer.append('\n');
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public XmlElement getParent() { return parent; }
}
