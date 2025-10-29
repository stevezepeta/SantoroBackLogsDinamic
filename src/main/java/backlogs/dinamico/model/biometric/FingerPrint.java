package backlogs.dinamico.model.biometric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "fingerPrint")
@CompoundIndexes({
        @CompoundIndex(name = "ux_fp_tenant_person", def = "{ 'tenant_id': 1, 'person_id': 1 } ", unique = true)
})
public class FingerPrint {

    @Field("tenant_id")
    private ObjectId tenantId;

    @Field("person_id")
    private ObjectId personId;

    private String thumbLeft;
    private String indexLeft;
    private String middleLeft;
    private String ringLeft;
    private String littleLeft;

    private String thumbRight;
    private String indexRight;
    private String middleRight;
    private String ringRight;
    private String littleRight;

}
