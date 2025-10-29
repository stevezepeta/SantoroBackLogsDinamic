package backlogs.dinamico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "backlogs.dinamico.config")
public class BacklogsApplication {

	public static void main(String[] args) {
		SpringApplication.run(BacklogsApplication.class, args);
	}

}
