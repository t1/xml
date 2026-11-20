package com.github.t1.xml;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.SneakyThrows;
import org.w3c.dom.Attr;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static java.lang.Boolean.FALSE;
import static java.util.Collections.unmodifiableList;
import static javax.xml.xpath.XPathConstants.NODESET;
import static org.w3c.dom.Node.ELEMENT_NODE;

@EqualsAndHashCode(callSuper = true)
public class XmlElement extends XmlNode {
    public interface XmlPosition {
        void add(XmlNode node, XmlElement relativeTo);
    }

    public static XmlPosition atBegin() {
        return (node, relativeTo) -> {
            NodeList siblings = relativeTo.element.getChildNodes();
            if (siblings.getLength() == 0) {
                relativeTo.addIndent();
                relativeTo.append(node.node);
            } else {
                Node reference = siblings.item(0);
                relativeTo.element.insertBefore(node.createText(relativeTo.indentString()), reference);
                relativeTo.element.insertBefore(node.node, reference);
            }
        };
    }

    public static XmlPosition atEnd() {
        return (node, relativeTo) -> {
            relativeTo.addIndent();
            relativeTo.append(node.node);
        };
    }

    public static XmlPosition before(String xpath) {
        return (node, relativeTo) -> {
            XmlElement reference = relativeTo.getXPathElement(xpath);
            before(reference).add(node, relativeTo);
        };
    }

    public static XmlPosition before(XmlElement reference) {
        return (node, relativeTo) -> {
            relativeTo.element.insertBefore(node.node, reference.element);
            relativeTo.element.insertBefore(node.createText(relativeTo.indentString()), reference.element);
        };
    }

    public Optional<XmlPosition> beforeExisting(String xpath) {
        return getOptionalElement(xpath).map(p -> before(xpath));
    }

    public Optional<XmlPosition> afterExisting(String xpath) {
        return getOptionalElement(xpath).map(p -> after(xpath));
    }

    public static XmlPosition after(String xpath) {
        return (node, relativeTo) -> {
            XmlElement reference = relativeTo.getXPathElement(xpath);
            if (reference.element.getNextSibling() != null) {
                relativeTo.element.insertBefore(node.node, reference.element.getNextSibling());
                relativeTo.element.insertBefore(node.createText(relativeTo.indentString()), reference.element.getNextSibling());
            } else {
                relativeTo.addIndent();
                relativeTo.append(node.node);
            }
        };
    }


    @Getter
    private final XmlElement parent;
    protected final Element element;
    private final int indent;

    private volatile String indentString;

    /** The text before the closing tag. Everything else goes before this. */
    private Text finalText;

    protected XmlElement(XmlElement parent, Element element, int indent) {
        super(parent, element);
        this.parent = parent;
        this.element = element;
        this.indent = indent;
    }


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
        if (parentNode instanceof Element)
            out = buildPath(parentNode, out);
        return out.resolve(e.getNodeName());
    }

    public boolean hasId() { return hasAttribute("id"); }

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
        List<XmlElement> result = new ArrayList<>();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            if (child instanceof Element e) {
                result.add(createChildElement(e));
            }
        }
        return unmodifiableList(result);
    }

    private XmlElement createChildElement(Element e) { return new XmlElement(this, e, indent + 1); }

    public List<Path> elementPaths() {
        List<Path> result = new ArrayList<>();
        for (XmlElement element : getChildNodes()) {
            result.add(element.getPath());
        }
        return unmodifiableList(result);
    }

    private List<XmlElement> getChildNodes() {
        List<XmlElement> result = new ArrayList<>();
        addChildNodes(element.getChildNodes(), result);
        return unmodifiableList(result);
    }

    private void addChildNodes(NodeList childNodes, List<XmlElement> result) {
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
        XPath xPath = XPathFactory.newInstance().newXPath();
        return list((NodeList) xPath.evaluate(xpath, element, NODESET));
    }

    public Optional<XmlElement> getOptionalElement(String path) { return getOptionalElement(Paths.get(path)); }

    public Optional<XmlElement> getOptionalElement(Path path) {
        int resultIndent = this.indent;
        Element node = element;
        for (Path pathElement : path) {
            List<Node> elements = elementsIn(node.getChildNodes(), e -> pathElement.toString().equals(e.getLocalName() != null ? e.getLocalName() : e.getTagName()));
            if (elements.isEmpty())
                return Optional.empty();
            if (elements.size() > 1)
                throw new IllegalArgumentException("found " + elements.size() + " elements '" + pathElement
                    + "' in '" + getPath() + "'");
            node = (Element) elements.get(0);
            resultIndent++;
        }
        if (node == null)
            return Optional.empty();
        return Optional.of(new XmlElement(this, node, resultIndent));
    }

    private List<Node> elementsIn(NodeList nodeList, Predicate<Element> predicate) {
        List<Node> result = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == ELEMENT_NODE && predicate.test((Element) node))
                result.add(node);
        }
        return result;
    }

    public boolean hasChildElement(String path) { return hasChildElement(Paths.get(path)); }

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
        return getOptionalElement(name).orElseGet(() -> addElement(name, position));
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
        XmlElement node = createChildElement(createElement(name));
        position.add(node, this);
        return node;
    }

    public void addNode(XmlNode node) {
        addIndent();
        // re-create the node in this element's namespace, so moved nodes don't carry their old
        // default namespace as xmlns attributes on every child
        append(adopt(node.node));
    }

    private Node adopt(Node node) {
        String namespaceURI = element.getNamespaceURI();
        if (node instanceof Element e && namespaceURI != null && !namespaceURI.equals(node.getNamespaceURI())) {
            // non-namespace-aware elements have no local name; fall back to the tag name
            String localName = e.getLocalName() != null ? e.getLocalName() : e.getTagName();
            Element copy = document().createElementNS(namespaceURI, localName);
            NamedNodeMap attributes = e.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attr = (Attr) attributes.item(i);
                if (!attr.getName().startsWith("xmlns"))
                    copy.setAttribute(attr.getName(), attr.getValue());
            }
            for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling())
                copy.appendChild(adopt(child));
            return copy;
        }
        return document().importNode(node, true);
    }

    void add(Node node, XmlPosition position) { position.add(createChildNode(node), this); }

    private XmlNode createChildNode(Node node) { return new XmlNode(this, node); }

    void append(Node node) { element.insertBefore(node, finalText()); }

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

    void addIndent() { element.insertBefore(createText(indentString()), finalText()); }

    private String indentString() {
        if (indentString == null)
            this.indentString = indentString(indent);
        return indentString;
    }

    private String indentString(int n) {
        return "\n" + "    ".repeat(Math.max(0, n));
    }

    public XmlElement removeAttribute(String name) { return setAttribute(name, null); }

    public XmlElement setAttribute(String name, String value) {
        if (value == null) {
            element.removeAttribute(name);
        } else if (name.equals("xmlns") || name.startsWith("xmlns:")) {
            // namespace declarations are not plain attributes: set them via the xmlns namespace,
            // after removing any existing declaration (the serializer derives them from the namespace)
            element.removeAttribute(name);
            element.setAttributeNS("http://www.w3.org/2000/xmlns/", name, value);
        } else {
            element.setAttribute(name, value);
        }
        return this;
    }

    public XmlElement addComment(String text, XmlPosition position) {
        position.add(createChildNode(createComment(text)), this);
        return this;
    }

    public XmlElement addComment(String text) {
        addIndent();
        append(createComment(text));
        return this;
    }

    public XmlElement nl() { return addText("\n"); }

    public XmlElement addText(String string) {
        // we don't use finalText here, as this may be an element without linebreaks
        // i.e. finalText may be null so the new text is inserted before the closing tag
        element.insertBefore(createText(string), finalText);
        return this;
    }

    public XmlElement setText(String string) {
        element.setTextContent(string);
        return this;
    }

    public String getText() {
        return element.getTextContent();
    }

    public void remove() {
        Node parent = element.getParentNode();
        Node indent = element.getPreviousSibling();
        parent.removeChild(element);
        parent.removeChild(indent);
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
        // we write the `<?xml ...` declaration manually, as the OpenJDK serializer doesn't append a nl!
        // see https://bugs.openjdk.java.net/browse/JDK-7150637
        append(writer, "<?xml version=\"" + document().getXmlVersion() + "\" encoding=\"UTF-8\"?>\n");
        LSSerializer serializer = serializer();
        serializer.getDomConfig().setParameter("xml-declaration", FALSE);
        serializer.write(element, createOutput(writer));
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
        append(writer, "\n");
    }

    private void append(Writer writer, String text) {
        try {
            writer.append(text);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
