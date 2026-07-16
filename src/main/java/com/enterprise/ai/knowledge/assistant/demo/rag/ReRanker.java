package com.enterprise.ai.knowledge.assistant.demo.rag;

import com.enterprise.ai.knowledge.assistant.demo.embedding.dto.EmbeddingResult;
import com.enterprise.ai.knowledge.assistant.demo.embedding.service.EmbeddingService;
import com.enterprise.ai.knowledge.assistant.demo.repository.SearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ReRanker - supports embedding-based and optional LLM-based scoring strategies.
 */
@Component
public class ReRanker {

	private final EmbeddingService embeddingService;
	private final ChatClient chatClient;
	private final String strategy;

	public ReRanker(EmbeddingService embeddingService,
					ChatClient chatClient,
					@Value("${app.reranker.strategy:embedding}") String strategy) {
		this.embeddingService = embeddingService;
		this.chatClient = chatClient;
		this.strategy = strategy == null ? "embedding" : strategy.toLowerCase(Locale.ROOT);
	}

	/**
	 * Rerank candidates and return top N results.
	 */
	public List<SearchResult> rerank(List<SearchResult> candidates, String query, int topN) {
		if (candidates == null || candidates.isEmpty()) return List.of();

		try {
			if ("llm".equals(strategy) && chatClient != null) {
				List<Double> scores = llmScoreCandidates(candidates, query);
				if (scores != null && scores.size() == candidates.size()) {
					List<SearchResult> scored = new ArrayList<>();
					for (int i = 0; i < candidates.size(); i++) {
						SearchResult r = candidates.get(i);
						double combined = (r.getScore() + scores.get(i)) / 2.0; // simple combine
						scored.add(new SearchResult(r.getContent(), combined, r.getPageNumber(), r.getDocumentName(), r.getChunkIndex()));
					}
					return scored.stream()
							.sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
							.limit(topN)
							.collect(Collectors.toList());
				}
				// fallback to embedding strategy if parsing failed
			}

			// Embedding-based re-ranking
			EmbeddingResult queryEmb = embeddingService.generateEmbedding(query);
			if (queryEmb == null || queryEmb.vector() == null) {
				return candidates.stream().limit(topN).collect(Collectors.toList());
			}
			float[] qv = queryEmb.vector();

			List<ScoredResult> scored = new ArrayList<>();
			for (SearchResult c : candidates) {
				try {
					EmbeddingResult ce = embeddingService.generateEmbedding(c.getContent());
					if (ce == null || ce.vector() == null) continue;
					double sim = cosineSimilarity(qv, ce.vector());
					double combined = (sim + c.getScore()) / 2.0; // combine store score and sim
					scored.add(new ScoredResult(c, combined));
				} catch (Exception ignored) {
				}
			}

			return scored.stream()
					.sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
					.map(ScoredResult::result)
					.limit(topN)
					.collect(Collectors.toList());

		} catch (Exception e) {
			// on any unexpected error, return best-effort topN
			return candidates.stream().limit(topN).collect(Collectors.toList());
		}
	}

	private static class ScoredResult {
		private final SearchResult result;
		private final double score;

		ScoredResult(SearchResult result, double score) {
			this.result = result;
			this.score = score;
		}

		public SearchResult result() { return result; }
		public double score() { return score; }
	}

	private double cosineSimilarity(float[] a, float[] b) {
		double dot = 0.0;
		double na = 0.0;
		double nb = 0.0;
		int len = Math.min(a.length, b.length);
		for (int i = 0; i < len; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		if (na == 0 || nb == 0) return 0.0;
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}

	private List<Double> llmScoreCandidates(List<SearchResult> candidates, String query) {
		// Build a compact scoring prompt. Expect a response containing one numeric score per item.
		StringBuilder sb = new StringBuilder();
		sb.append("You are a relevance scorer. Given the user query and candidate document excerpts, provide a relevance score between 0 and 1 for each candidate in the original order, separated by commas. No extra text.\n");
		sb.append("Query:\n").append(query).append("\n\n");
		for (int i = 0; i < candidates.size(); i++) {
			SearchResult c = candidates.get(i);
			sb.append("Candidate ").append(i + 1).append(": \n");
			sb.append(c.getContent()).append("\n\n");
		}

		String resp = chatClient.prompt()
				.system("You are a concise scorer bot.")
				.user(sb.toString())
				.call()
				.content();

		if (resp == null || resp.isBlank()) return null;

		// extract numbers from response
		Pattern p = Pattern.compile("([0-1]?\\.?\\d+)");
		Matcher m = p.matcher(resp);
		List<Double> scores = new ArrayList<>();
		while (m.find()) {
			try {
				double v = Double.parseDouble(m.group(1));
				// clamp to 0..1
				if (v < 0) v = 0;
				if (v > 1) v = 1;
				scores.add(v);
			} catch (NumberFormatException ignored) {}
		}

		return scores;
	}
}
