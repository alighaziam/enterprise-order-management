package io.github.alighaziam.eom;
import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling public class EnterpriseOrderManagementApplication {
 public static void main(String[] args){SpringApplication.run(EnterpriseOrderManagementApplication.class,args);}
}