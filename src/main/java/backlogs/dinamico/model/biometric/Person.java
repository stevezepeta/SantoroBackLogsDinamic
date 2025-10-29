package backlogs.dinamico.model.biometric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "persons")
@CompoundIndexes({
        @CompoundIndex(name = "ux_person_tenant_curp",
                        def="{ 'tenant_id': 1, 'curp': 1 }",
                        unique = true)
})
public class Person {

    @Id
    private ObjectId id;

    @Field("tenant_id")
    private ObjectId tenantId;

    private String curp;

    private String name;

    @Field("face_photo")
    private String facePhotoPath; // ruta en el disco

}
