import com.github.t1.xml.Xml;
import com.github.t1.xml.XmlElement;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

class XmlTest {
    private static final URL URL = Optional.ofNullable(XmlTest.class.getResource("sample.xml")).orElseThrow();
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

    @Test void shouldHaveToXmlString() {
        assertThat(xml.toXmlString()).isEqualTo(XML);
    }

    @Test void shouldBeEqualToFromString() {
        Xml actual = Xml.fromString(XML);
        assertThat(actual).hasToString(xml.toString());
    }

    @Test void shouldLoadFromFile() throws URISyntaxException {
        val actual = Xml.load(URL.toURI());

        assertThat(actual.toXmlString()).isEqualTo(XML);
        assertThat(actual.uri()).isEqualTo(URL.toURI());
        assertThat(actual.uri().toString())
            .isEqualTo("file:" + System.getProperty("user.dir") + "/target/test-classes/sample.xml");
    }

    @Test void shouldKeepDefaultNamespaceOnRootElement() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
            </project>
            """;

        val actual = Xml.fromString(pom);

        assertThat(actual.toXmlString()).isEqualTo(pom);
    }

    @Test void shouldFindElementsInDefaultNamespace() {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <build>
                    <plugins>
                        <plugin><artifactId>nexus-staging-maven-plugin</artifactId></plugin>
                        <plugin><artifactId>other-plugin</artifactId></plugin>
                    </plugins>
                </build>
            </project>
            """;

        val xml = Xml.fromString(pom);

        assertThat(xml.find("//*[local-name()='plugin']")).hasSize(2);
        val nexus = xml.find("//*[local-name()='plugin'][*[local-name()='artifactId']='nexus-staging-maven-plugin']");
        assertThat(nexus).hasSize(1);
        nexus.forEach(XmlElement::remove);
        assertThat(xml.find("//*[local-name()='plugin']")).hasSize(1);
        assertThat(xml.toXmlString()).doesNotContain("nexus-staging-maven-plugin");
    }

    @Test void shouldSaveWithXmlDeclarationOnOwnLineAndKeepNamespace() throws Exception {
        val pom = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
            </project>
            """;
        val file = Files.createTempFile("xml-test", ".xml");
        try {
            val xml = Xml.fromString(pom).uri(file.toUri());
            xml.getOrCreateElement("name").setText("x");
            xml.save();

            val saved = Files.readString(file);
            assertThat(saved).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\"");
            assertThat(saved).contains("<name>x</name>");
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
