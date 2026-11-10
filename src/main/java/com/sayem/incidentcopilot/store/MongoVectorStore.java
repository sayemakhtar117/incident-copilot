package com.sayem.incidentcopilot.store;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.sayem.incidentcopilot.model.Incident;
import com.sayem.incidentcopilot.model.ScoredIncident;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB Atlas store using the {@code $vectorSearch} aggregation stage.
 * Requires an Atlas Vector Search index on the {@code embedding} field (see
 * README for the index definition; M0 free clusters support it).
 */
public class MongoVectorStore implements VectorStore {

	private final MongoTemplate template;
	private final String collection;
	private final String indexName;

	public MongoVectorStore(MongoTemplate template, String collection, String indexName) {
		this.template = template;
		this.collection = collection;
		this.indexName = indexName;
	}

	private MongoCollection<Document> coll() {
		return template.getCollection(collection);
	}

	/**
	 * Upserts an incident document and its vector into the configured MongoDB
	 * collection.
	 */
	@Override
	public void upsert(Incident i, float[] embedding) {
		List<Double> vec = new ArrayList<>(embedding.length);
		for (float f : embedding) {
			vec.add((double) f);
		}
		Document doc = new Document("_id", i.id()).append("title", i.title()).append("service", i.service())
				.append("severity", i.severity()).append("symptoms", i.symptoms()).append("rootCause", i.rootCause())
				.append("resolution", i.resolution()).append("occurredOn", i.occurredOn()).append("embedding", vec);
		coll().replaceOne(Filters.eq("_id", i.id()), doc, new ReplaceOptions().upsert(true));
	}

	/**
	 * Runs Atlas vector search for the query embedding and returns the requested
	 * top matches with scores.
	 *
	 * @param queryVector normalized query embedding
	 * @param k           maximum number of results
	 * @return nearest incidents ordered by vector-search score
	 */
	@Override
	public List<ScoredIncident> search(float[] queryVector, int k) {
		List<Double> q = new ArrayList<>(queryVector.length);
		for (float f : queryVector) {
			q.add((double) f);
		}
		List<Document> pipeline = List.of(
				new Document("$vectorSearch",
						new Document("index", indexName).append("path", "embedding").append("queryVector", q)
								.append("numCandidates", Math.max(50, k * 10)).append("limit", k)),
				new Document("$addFields", new Document("score", new Document("$meta", "vectorSearchScore"))),
				new Document("$project", new Document("embedding", 0)));
		List<ScoredIncident> out = new ArrayList<>();
		for (Document d : coll().aggregate(pipeline)) {
			Number score = d.get("score", Number.class);
			out.add(new ScoredIncident(fromDoc(d), score == null ? 0 : score.doubleValue()));
		}
		return out;
	}

	/**
	 * Returns all incident documents ordered by ID, excluding stored embedding
	 * vectors.
	 */
	@Override
	public List<Incident> all() {
		List<Incident> out = new ArrayList<>();
		for (Document d : coll().find().projection(new Document("embedding", 0)).sort(new Document("_id", 1))) {
			out.add(fromDoc(d));
		}
		return out;
	}

	/** Returns the number of documents in the configured collection. */
	@Override
	public long count() {
		return coll().countDocuments();
	}

	/**
	 * Identifies the Atlas vector-search implementation in API status responses.
	 */
	@Override
	public String name() {
		return "mongodb-atlas-vector-search";
	}

	private static Incident fromDoc(Document d) {
		return new Incident(d.getString("_id"), d.getString("title"), d.getString("service"), d.getString("severity"),
				d.getString("symptoms"), d.getString("rootCause"), d.getString("resolution"),
				d.getString("occurredOn"));
	}
}
