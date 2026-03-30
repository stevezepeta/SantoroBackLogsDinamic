package backlogs.dinamico;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "backlogs.dinamico.repository")
public class BacklogsApplication {

	public static void main(String[] args) {
		SpringApplication.run(BacklogsApplication.class, args);
	}

}
