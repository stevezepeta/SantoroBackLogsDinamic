package backlogs.dinamico.service.support;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SequenceService {

    private final MongoTemplate mongo;

    public long next(String key) {

        Query q = new Query(Criteria.where("_id").is(key));
        Update u = new Update().inc("seq", 1);

        FindAndModifyOptions opts = new FindAndModifyOptions()
                .upsert(true)
                .returnNew(true);

        Document doc = mongo.findAndModify(q, u, opts, Document.class, "counters");

        if (doc == null || !doc.containsKey("seq")) {
            return 1L;
        }

        return doc.get("seq", Number.class).longValue();
    }

    public static String officeKey(ObjectId tenantId) {
        return "office:" + tenantId.toHexString();
    }

}
