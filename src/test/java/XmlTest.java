import com.github.t1.xml.Xml;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XmlTest {
    private static final String XML = "" +
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<root>\n" +
            "    <!-- my-comment -->\n" +
            "</root>\n";
    private final Xml xml = build();

    private Xml build() {
        Xml xml = Xml.createWithRootElement("root");
        xml.addComment("my-comment");
        return xml;
    }

    @Test
    public void shouldHaveToXmlString() {
        assertEquals(XML, xml.toXmlString());
    }

    @Test
    @Ignore
    public void shouldBeEqualToFromString() {
        Xml actual = Xml.fromString(XML);
        assertEquals(xml, actual);
    }
}
