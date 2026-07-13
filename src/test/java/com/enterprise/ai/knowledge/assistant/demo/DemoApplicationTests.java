package com.enterprise.ai.knowledge.assistant.demo;

import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

		EmbeddingResult embedding = embeddingService.generateEmbedding(text);
		float[] vector = embedding.vector();
		System.out.println("Vector size : " + (vector == null ? 0 : vector.length));
		System.out.println(Arrays.toString(Arrays.copyOf(vector, Math.min(10, vector == null ? 0 : vector.length))));
	}

}
