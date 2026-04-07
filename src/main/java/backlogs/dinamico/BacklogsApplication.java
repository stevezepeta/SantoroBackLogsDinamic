package backlogs.dinamico;

import backlogs.dinamico.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "backlogs.dinamico.config")
@EnableConfigurationProperties(RateLimitProperties.class)
@EnableAsync
@EnableScheduling
public class BacklogsApplication {

	public static void main(String[] args) {
		SpringApplication.run(BacklogsApplication.class, args);
	}

}
