package backlogs.dinamico.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "biometric")
public class BiometricProps {

    private String dirFp = "./data/fp";
    private String firFce = "./data/face";
    private double threshold = 25.0;

}
