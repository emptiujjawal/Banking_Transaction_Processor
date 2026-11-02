package bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }

    /** Declared here so the domain classes stay free of framework annotations. */
    @Bean
    BankingService bankingService() {
        return new BankingService();
    }
}
