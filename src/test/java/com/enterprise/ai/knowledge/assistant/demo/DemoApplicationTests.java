package com.enterprise.ai.knowledge.assistant.demo;

import com.enterprise.ai.knowledge.assistant.demo.embedding.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

@SpringBootTest
class DemoApplicationTests {

	@Autowired
	private EmbeddingService embeddingService;

	@Test
	void contextLoads() {
	}

	@Test
	//@Disabled("Requires LM Studio running with a loaded model. Run manually when LM Studio is available.")
	void testDummy() {
		String text = "Employees receive 20 days of paid time off.";

		float[] embedding = embeddingService.generateEmbedding(text);

		System.out.println("Vector size : " + embedding.length);
		System.out.println(Arrays.toString(Arrays.copyOf(embedding, 10)));
	}

}
