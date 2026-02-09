package backlogs.dinamico;

import backlogs.dinamico.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "backlogs.dinamico.config")
@EnableConfigurationProperties(RateLimitProperties.class)
public class BacklogsApplication {

	public static void main(String[] args) {
		SpringApplication.run(BacklogsApplication.class, args);
	}

}
