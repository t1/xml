import com.github.t1.xml.Xml;
import lombok.val;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

public class XmlTest {
    private static final URL URL = XmlTest.class.getResource("sample.xml");
    private static final String XML = contentOf(URL);
    private final Xml xml = build();

    private Xml build() {
        val xml = Xml.createWithRootElement("root");
        xml.addComment("my-comment");
        xml.getOrCreateElement("elem")
                .setAttribute("attr", "val")
                .addText("foo")
                .addText("bar");
        return xml;
    }

    @Test
    public void shouldHaveToXmlString() {
        assertThat(xml.toXmlString()).isEqualTo(XML);
    }

    @Test
    public void shouldBeEqualToFromString() {
        Xml actual = Xml.fromString(XML);
        assertThat(actual).hasToString(xml.toString());
    }

    @Test
    public void shouldLoadFromFile() throws URISyntaxException {
        val actual = Xml.load(URL.toURI());

        assertThat(actual.toXmlString()).isEqualTo(XML);
        assertThat(actual.uri()).isEqualTo(URL.toURI());
        assertThat(actual.uri().toString())
                .isEqualTo("file:" + System.getProperty("user.dir") + "/target/test-classes/sample.xml");
    }
}
