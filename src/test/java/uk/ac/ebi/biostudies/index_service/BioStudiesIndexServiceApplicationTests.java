package uk.ac.ebi.biostudies.index_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;


@SpringBootTest
@ActiveProfiles("test")
class BioStudiesIndexServiceApplicationTests {

	@TempDir
	static java.nio.file.Path tempDir;

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("index.base-dir", () -> tempDir.toAbsolutePath().toString());
	}

	@Test
	void contextLoads() {
	}

}
