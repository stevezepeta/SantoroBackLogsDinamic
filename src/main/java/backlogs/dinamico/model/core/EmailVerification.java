package backlogs.dinamico.model.core;

import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

/**
 * Registro de verificación de email por OTP.
 * Colección: email_verifications
 *
 * Estados:
 *   PENDING   → OTP enviado, esperando confirmación
 *   VERIFIED  → OTP confirmado, verificationToken emitido
 *   EXPIRED   → TTL superado o intentos agotados
 *   USED      → verificationToken ya fue consumido al crear invite/org
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "email_verifications")
@CompoundIndexes({
        @CompoundIndex(name = "idx_ev_email_status",
                def = "{ 'email_ci': 1, 'status': 1 }"),
        @CompoundIndex(name = "idx_ev_verification_token",
                def = "{ 'verification_token': 1 }", sparse = true)
})
public class EmailVerification {

    @Id
    private ObjectId id;

    @Field("email")
    private String email;

    @Field("email_ci")
    private String emailCi;     // lowercase para búsquedas

    @Field("otp_hash")
    private String otpHash;     // bcrypt del OTP — nunca guardar en claro

    @Field("otp_expires_at")
    private Instant otpExpiresAt;   // 10 minutos

    @Field("attempts")
    @Builder.Default
    private int attempts = 0;       // máx 3 intentos fallidos

    // Una vez confirmado se emite este token (UUID) con TTL de 15 min
    @Field("verification_token")
    private String verificationToken;

    @Field("token_expires_at")
    private Instant tokenExpiresAt;

    // PENDING | VERIFIED | EXPIRED | USED
    private String status;

    // Para debug / auditoría
    @Field("mx_valid")
    private boolean mxValid;

    @Field("tenant_id")
    private ObjectId tenantId;      // opcional — para contexto

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    // MongoDB TTL index — borra documentos EXPIRED/USED después de 24h
    @Indexed(expireAfterSeconds = 86400)
    @Field("expires_at")
    private Instant expiresAt;
}